package flowcase

import no.jpro.slack.cv.flowcase.FlowcaseService
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
class FlowcaseClientIT {


    val client = FlowcaseService.connect(token = "Token mot CVPartner")

    @Test
    fun `getCV`() {
        println(client.user("hp@jpro.no"))
    }
}
