import no.jpro.slack.cv.flowcase.FlowcaseService

fun translatedValue(norwegian: String) = FlowcaseService.TranslatedValue(norwegian, "")
fun projectExperience() = FlowcaseService.ProjectExperiences(
    _id = "abc123",
    disabled = false,
    customer = translatedValue("Kunde 1"),
    description = translatedValue("Beskrivelse"),
    long_description = translatedValue("Lang beskrivelse"),
    month_from = "12",
    month_to = "12",
    year_from = "2024",
    year_to = "2025",
    roles = emptyList(),
    project_experience_skills = emptyList(),
)
fun keyQualification() = FlowcaseService.KeyQualifications(
    _id = "abc123",
    disabled = false,
    tag_line = translatedValue("Tag line"),
    long_description = translatedValue("Lang beskrivelse"),
)

fun createCv() = FlowcaseService.FlowcaseCv(
    name = "Kåre Konsulent",
    project_experiences = listOf(projectExperience()),
    key_qualifications = listOf(keyQualification()),
)
