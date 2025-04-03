package flowcase

import no.jpro.slack.cv.flowcase.FlowcaseClient
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
class FlowcaseClientIT {


    val client = FlowcaseClient(token = "Token mot CVPartner")

    @Test
    fun `getCV`() {
        println(client.getCV("hp@jpro.no"))
    }
}