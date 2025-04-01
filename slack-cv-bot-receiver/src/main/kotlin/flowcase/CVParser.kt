package no.jpro.slack.cv.flowcase

import com.jayway.jsonpath.JsonPath

class CVParser(cvJson: String) {

    private val cvJpath = JsonPath.parse(cvJson)
    
    fun parseCV(): CV {
        return CV(
            name = cvJpath.read("$.name"),
            projects = parseProjects(),
            summary = parseSummary(),
            education = parseEducations()
        )
    }
    
    fun parseSummary(): String {
        /*A user can have more than one summary in their CVPartner CV,
     however, only one can be active at a time. This fn selects the
     current active summary and notifies if there are more than one. */
        val activeSummary =
            cvJpath.read<List<String>>("$.key_qualifications[?(@.disabled ==false)]..long_description.no")
        return activeSummary.first()
    }

    fun parseEducations(): List<Education> {
        val educations = cvJpath.read<List<Map<String, Any>>>("$.educations[?(@.disabled ==false)]")
            .map { parseSingleEducation(it) }
        return educations
    }

    private fun parseSingleEducation(education: Map<String, Any>): Education {
        val jpathEducation = JsonPath.parse(education)
        return Education(
            school = jpathEducation.read("$.school.no"),
            degree = jpathEducation.read("$.degree.no"),
            description = jpathEducation.read("$.description.no"),
            start_year = jpathEducation.read("$.year_from"),
            end_year = jpathEducation.read("$.year_to")
        )
    }

    fun parseProjects(): List<Project> {
        return cvJpath.read<List<Map<String, Any>>>("$.project_experiences[?(@.disabled ==false)]")
            .map { parseSingleProject(it) }
            .sortedByDescending { it.startDate }
    }

    private fun parseSingleProject(project: Map<String, Any>): Project {
        val jpathProject = JsonPath.parse(project)

        val skills = jpathProject.read<List<String>>("$.project_experience_skills..tags.no")

        val roles = jpathProject.read<List<Map<String, Any>>>("$.roles[?(@.disabled ==false)]")
            .map { parseProjectRole(it) }

        return Project(
            customer = jpathProject.read("$.customer.no"),
            name = jpathProject.read("$.description.no"),
            description = jpathProject.read("$.long_description.no"),
            roles = roles,
            skills = skills,
            startYear = jpathProject.read("$.year_from"),
            startMonth = jpathProject.read("$.month_from"),
            endYear = jpathProject.read("$.year_to"),
            endMonth = jpathProject.read("$.month_to"),
        )

    }

    private fun parseProjectRole(role: Map<String, Any>): ProjectRole {
        val jpathRole = JsonPath.parse(role)
        return ProjectRole(
            name = jpathRole.read("$.name.no"),
            description = jpathRole.read("$.long_description.no"),
        )
    }
}