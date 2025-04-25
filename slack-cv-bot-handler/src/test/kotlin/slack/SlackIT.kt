package slack

import com.slack.api.Slack
import com.slack.api.methods.kotlin_extension.request.chat.blocks
import com.slack.api.model.block.DividerBlock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.OptionObject
import com.slack.api.model.block.composition.PlainTextObject
import com.slack.api.model.block.element.StaticSelectElement
import createCv
import no.jpro.slack.cv.SectionDetails
import no.jpro.slack.cv.flowcase.FlowcaseService
import no.jpro.slack.cv.getOptionText_NO
import no.jpro.slack.cv.markdownQuoteBlock
import no.jpro.slack.cv.whichSectionQuestion
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@Disabled
class SlackIT {

    private val slackBotToken = "replace-me" // starts with 'xoxb-'
    private val slack = Slack.getInstance().methods(slackBotToken)
    private val channelId = "C08LWCP0LTT" // #cv-prat-test

    @Test
    fun testMarkdown() {
        val multilineDescription = """
            Prosjekt 1 var kult.
            Jeg fikset ting.
            Julen var hvit det året.
        """.trimIndent()
        val sectionDetails = SectionDetails("ABC", "Hei?", multilineDescription)

        val result = slack.chatPostMessage {
            val text = "Sender *${sectionDetails.title}* til OpenAI for vurdering"
            it
                .channel(channelId)
                .text(text)
                .blocks {
                    section { markdownText(text) }
                    sectionDetails.description?.let { description -> section { markdownText(description.markdownQuoteBlock()) } }
                }
        }

        assertTrue(result.isOk)
    }

    @Test
    fun testDropdown() {
        val result = slack.chatPostMessage {
            it.channel(channelId)
                .text(whichSectionQuestion)
                .blocks(listOf(DividerBlock.builder().build()) + listOf(questionSection(createCv())))
        }

        assertTrue(result.isOk)
    }
}

private fun questionSection(cv: FlowcaseService.FlowcaseCv): SectionBlock {
    val keyQualificationOptionElements = cv.key_qualifications.orEmpty()
        .filter { !it.disabled }
        .map { keyQualification ->
            OptionObject.builder()
                .text(PlainTextObject("Sammendrag", false))
                .value("key_qualification-${keyQualification._id}")
                .build()
        }

    val projectExperienceOptionElements = cv.project_experiences.orEmpty()
        .filter { !it.disabled }
        .sortedWith(compareBy({ it.year_from }, { it.month_from }))
        .reversed()
        .map { projectExperience ->
            OptionObject.builder()
                .text(PlainTextObject(getOptionText_NO(projectExperience), false))
                .value("project_experience-${projectExperience._id}")
                .build()
        }
    val options = keyQualificationOptionElements + projectExperienceOptionElements

    return SectionBlock.builder()
        .text(PlainTextObject(whichSectionQuestion, true))
        .accessory(
            StaticSelectElement.builder()
                .placeholder(PlainTextObject("Velg", true))
                .options(options)
                .build()
        )
        .build()
}
