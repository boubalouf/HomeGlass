package com.homeglass.app.data.presence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.homeglass.app.MainActivity
import com.homeglass.app.R
import com.homeglass.app.domain.presence.PresenceEntry
import com.homeglass.app.domain.presence.PresencePlanner
import com.homeglass.app.domain.presence.PresenceSource
import com.homeglass.app.domain.presence.PresenceTuning
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Réveil de la tablette au passage d'une personne.
 *
 * Le déclencheur est choisi dans les réglages :
 * - **automatique** : capteur de proximité **et** caméra quand ils existent. Le capteur réveille
 *   instantanément une main posée près du bord, la caméra détecte les personnes à distance —
 *   indispensable sur les tablettes dont le capteur est binaire (0 = près, 260 = loin) ;
 * - **capteur de proximité** : uniquement lui ;
 * - **caméra** : uniquement l'analyse de visage.
 *
 * Chaque réveil est inscrit dans le journal de présence (heure, détecteur, distance quand elle
 * est connue), visible dans les réglages. Aucune image n'est conservée ni transmise.
 */
class PresenceService : LifecycleService() {

    private var provider: ProcessCameraProvider? = null
    private var detector: FaceDetector? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastAnalysis = 0L
    private var lastWakeUp = 0L
    private var sensorManager: SensorManager? = null
    private var proximityListener: SensorEventListener? = null
    private var appliedSource: PresenceSource? = null

    private val store by lazy { PresenceStore(applicationContext) }
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Réglages lus en direct : modifiés dans l'écran Réglages, appliqués sans redémarrage. */
    private var thresholdCm = PresenceTuning.DEFAULT_THRESHOLD_CM
    private var cooldownMs = PresenceTuning.DEFAULT_COOLDOWN_MS

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        settingsScope.launch {
            store.source.collect { source ->
                if (source != appliedSource) {
                    appliedSource = source
                    applySource(source)
                }
            }
        }
        settingsScope.launch {
            store.thresholdCm.collect { value -> thresholdCm = value }
        }
        settingsScope.launch {
            store.cooldownMs.collect { value -> cooldownMs = value }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    /** Active les détecteurs du mode choisi, puis inscrit le démarrage au journal. */
    private fun applySource(source: PresenceSource) {
        stopDetectors()

        val plan = PresencePlanner.plan(
            source = source,
            hasProximity = hasProximitySensor(),
            hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
        )
        if (plan.useProximity) startProximity()
        if (plan.useCamera) startCamera()

        record(
            PresenceEntry(
                at = System.currentTimeMillis(),
                source = source,
                detail = getString(R.string.presence_log_started),
            ),
        )
    }

    private fun hasProximitySensor(): Boolean {
        val manager = getSystemService(SensorManager::class.java) ?: return false
        return manager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
    }

    /** Enregistre le capteur de proximité ; faux si l'appareil n'en a pas. */
    private fun startProximity(): Boolean {
        val manager = getSystemService(SensorManager::class.java) ?: return false
        // Variante « wake-up » quand elle existe : le noyau réveille le SoC pour livrer
        // l'événement, ce qui évite d'attendre la prochaine fenêtre d'activité.
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            ?: return false
        val maxRange = sensor.maximumRange

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val distance = event.values.firstOrNull() ?: return
                // Deux familles de capteurs : binaire (0 = près, portée ~5) et de distance
                // (centimètres, avec une valeur « rien devant » au-delà de la portée).
                // Le seuil réglé par l'utilisateur ne peut pas dépasser la portée du capteur.
                val limit = if (maxRange > 0f) minOf(maxRange, thresholdCm) else thresholdCm
                if (distance < limit) {
                    wakeUp(
                        source = PresenceSource.Proximity,
                        detail = getString(
                            R.string.settings_presence_distance_value,
                            distance.roundToInt(),
                        ),
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }.getOrDefault(false)
        if (!registered) return false

        sensorManager = manager
        proximityListener = listener
        return true
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                runCatching {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        Log.w(TAG, "Aucune caméra frontale : réveil par caméra indisponible")
                        return@addListener
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { proxy -> analyse(proxy) }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        analysis,
                    )
                }.onFailure { error ->
                    Log.w(TAG, "Caméra frontale inaccessible", error)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyse(proxy: ImageProxy) {
        val mediaImage = proxy.image
        val now = SystemClock.elapsedRealtime()
        // Écran déjà allumé : rien à détecter, on libère la frame tout de suite.
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive == true
        if (mediaImage == null || interactive || now - lastAnalysis < FRAME_INTERVAL_MS) {
            proxy.close()
            return
        }
        lastAnalysis = now

        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        detector?.process(input)
            ?.addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) wakeUp(source = PresenceSource.Camera, detail = null)
            }
            ?.addOnCompleteListener { proxy.close() }
            ?: proxy.close()
    }

    /**
     * Rallume l'écran et ramène l'application, au plus une fois par anti-rebond réglé.
     * L'événement est inscrit au journal avec le détecteur qui l'a déclenché.
     */
    private fun wakeUp(source: PresenceSource, detail: String?) {
        val power = getSystemService(PowerManager::class.java) ?: return
        if (power.isInteractive) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeUp < cooldownMs) return
        lastWakeUp = now

        record(PresenceEntry(at = System.currentTimeMillis(), source = source, detail = detail))

        val lock = wakeLock ?: power
            .newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                WAKE_LOCK_TAG,
            )
            .also { wakeLock = it }
        lock.acquire(WAKE_TIMEOUT_MS)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
            putExtra(EXTRA_WAKE_UP, true)
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Impossible de ramener le tableau de bord", it) }
    }

    private fun record(entry: PresenceEntry) {
        settingsScope.launch { store.record(entry) }
    }

    private fun stopDetectors() {
        proximityListener?.let { listener ->
            runCatching { sensorManager?.unregisterListener(listener) }
        }
        proximityListener = null
        sensorManager = null
        runCatching { provider?.unbindAll() }
    }

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.presence_channel_name),
                    NotificationManager.IMPORTANCE_MIN,
                ),
            )
        }
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.presence_notification_active))
            .setContentIntent(content)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        stopDetectors()
        runCatching { detector?.close() }
        detector = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        settingsScope.cancel()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_WAKE_UP = "com.homeglass.app.extra.WAKE_UP"

        private const val TAG = "PresenceService"
        private const val CHANNEL_ID = "presence"
        private const val NOTIFICATION_ID = 41
        private const val WAKE_LOCK_TAG = "homeglass:presence"
        private const val FRAME_INTERVAL_MS = 400L
        private const val WAKE_TIMEOUT_MS = 10_000L

        fun start(context: Context) {
            val intent = Intent(context, PresenceService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PresenceService::class.java)) }
        }
    }
}
