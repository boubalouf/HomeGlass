package com.homeglass.app.domain.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaProtocolTest {

    private fun json(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private fun array(raw: String) = Json.parseToJsonElement(raw)

    @Test
    fun `auth required is parsed with the version`() {
        val message = HaProtocol.parse("""{"type":"auth_required","ha_version":"2025.1.0"}""")
        assertEquals(HaMessage.AuthRequired("2025.1.0"), message)
    }

    @Test
    fun `auth results are parsed`() {
        assertEquals(HaMessage.AuthOk, HaProtocol.parse("""{"type":"auth_ok","ha_version":"2025.1"}"""))
        assertEquals(
            HaMessage.AuthInvalid("Invalid access token"),
            HaProtocol.parse("""{"type":"auth_invalid","message":"Invalid access token"}"""),
        )
    }

    @Test
    fun `successful result carries its payload`() {
        val message = HaProtocol.parse(
            """
            {"id":1,"type":"result","success":true,"result":[
              {"entity_id":"light.salon","state":"on","attributes":{"friendly_name":"Lampe","brightness":178}}
            ]}
            """.trimIndent(),
        )

        assertTrue(message is HaMessage.Result)
        message as HaMessage.Result
        assertTrue(message.success)
        assertEquals(1, message.id)
        assertEquals(null, message.error)

        val states = EntityState.listFromJson(message.result!!)
        assertEquals(1, states.size)
        assertEquals("light.salon", states.single().entityId)
        assertEquals("Lampe", states.single().friendlyName)
        assertEquals(178.0, states.single().numberAttribute("brightness")!!, 0.001)
    }

    @Test
    fun `failed result carries the error code`() {
        val message = HaProtocol.parse(
            """{"id":7,"type":"result","success":false,"error":{"code":"invalid_format","message":"Bad entity"}}""",
        ) as HaMessage.Result

        assertEquals(7, message.id)
        assertEquals(false, message.success)
        assertEquals("invalid_format", message.error?.code)
        assertEquals("Bad entity", message.error?.message)
    }

    @Test
    fun `state changed events are decoded`() {
        val message = HaProtocol.parse(
            """
            {"id":3,"type":"event","event":{"event_type":"state_changed","data":{
              "entity_id":"light.salon",
              "new_state":{"entity_id":"light.salon","state":"off","attributes":{}}
            }}}
            """.trimIndent(),
        ) as HaMessage.Event

        assertEquals(HaProtocol.EVENT_STATE_CHANGED, message.eventType)
        assertEquals("light.salon", HaProtocol.entityIdFromEvent(message))
        assertEquals("off", HaProtocol.newStateFromEvent(message)?.state)
    }

    @Test
    fun `removal events keep the entity id but have no new state`() {
        val message = HaProtocol.parse(
            """{"id":3,"type":"event","event":{"event_type":"state_changed","data":{"entity_id":"light.salon","new_state":null}}}""",
        ) as HaMessage.Event

        assertEquals("light.salon", HaProtocol.entityIdFromEvent(message))
        assertNull(HaProtocol.newStateFromEvent(message))
    }

    @Test
    fun `pong and unknown messages are tolerated`() {
        assertEquals(HaMessage.Pong(12), HaProtocol.parse("""{"id":12,"type":"pong"}"""))
        assertEquals(HaMessage.Unknown("future_event"), HaProtocol.parse("""{"type":"future_event"}"""))
        assertEquals(HaMessage.Unknown(null), HaProtocol.parse("{ pas du json"))
        assertEquals(HaMessage.Unknown(null), HaProtocol.parse("[]"))
    }

    @Test
    fun `auth command is well formed`() {
        val command = json(HaProtocol.auth("token123"))
        assertEquals("auth", command["type"]?.jsonPrimitive?.content)
        assertEquals("token123", command["access_token"]?.jsonPrimitive?.content)
        assertNull(command["token"])
    }

    @Test
    fun `auth command repeats the token for older add-on builds`() {
        val command = json(HaProtocol.auth("token123", includeLegacyToken = true))
        assertEquals("token123", command["access_token"]?.jsonPrimitive?.content)
        assertEquals("token123", command["token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `requests and subscriptions are well formed`() {
        assertEquals("get_states", json(HaProtocol.getStates(1))["type"]?.jsonPrimitive?.content)
        assertEquals(1, json(HaProtocol.getStates(1))["id"]?.jsonPrimitive?.content?.toInt())

        val subscription = json(HaProtocol.subscribeEvents(2))
        assertEquals("subscribe_events", subscription["type"]?.jsonPrimitive?.content)
        assertEquals("state_changed", subscription["event_type"]?.jsonPrimitive?.content)

        assertEquals(
            "config/area_registry/list",
            json(HaProtocol.listAreas(3))["type"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "config/entity_registry/list",
            json(HaProtocol.listEntities(4))["type"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "config/device_registry/list",
            json(HaProtocol.listDevices(5))["type"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `call service command carries data and target`() {
        val command = json(
            HaProtocol.callService(
                id = 9,
                domain = "light",
                service = "turn_on",
                data = json("""{"brightness_pct":40}"""),
                target = json("""{"entity_id":"light.salon"}"""),
            ),
        )

        assertEquals("call_service", command["type"]?.jsonPrimitive?.content)
        assertEquals("light", command["domain"]?.jsonPrimitive?.content)
        assertEquals("turn_on", command["service"]?.jsonPrimitive?.content)
        assertEquals(
            "light.salon",
            command["target"]?.jsonObject?.get("entity_id")?.jsonPrimitive?.content,
        )
        assertEquals(
            "40",
            command["service_data"]?.jsonObject?.get("brightness_pct")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `registry lists are decoded and tolerate unknown fields`() {
        val areas = HaRegistry.areasFromJson(
            array("""[{"area_id":"salon","name":"Salon","icon":"mdi:sofa","floor_id":"rdc","extra":1}]"""),
        )
        assertEquals(1, areas.size)
        assertEquals("Salon", areas.single().name)

        val entities = HaRegistry.entitiesFromJson(
            array(
                """[{"entity_id":"light.salon","area_id":null,"device_id":"dev1","disabled_by":null,"hidden_by":null,"entity_category":null}]""",
            ),
        )
        assertEquals("dev1", entities.single().deviceId)
        assertTrue(entities.single().isVisible)

        val devices = HaRegistry.devicesFromJson(array("""[{"id":"dev1","area_id":"salon"}]"""))
        val registry = HaRegistry(areas = areas, devices = devices, entities = entities)
        assertEquals("salon", registry.areaOf(entities.single()))
    }
}
