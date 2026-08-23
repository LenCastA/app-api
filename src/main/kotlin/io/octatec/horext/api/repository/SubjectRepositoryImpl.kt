package io.octatec.horext.api.repository

import io.octatec.horext.api.domain.OrganizationUnit
import io.octatec.horext.api.domain.Subject
import io.octatec.horext.api.dto.CourseAffiliation
import io.octatec.horext.api.dto.OrganizationUnitSummary
import io.octatec.horext.api.dto.Page
import io.octatec.horext.api.repository.table.Courses
import io.octatec.horext.api.repository.table.OrganizationUnits
import io.octatec.horext.api.repository.table.ScheduleSubjects
import io.octatec.horext.api.repository.table.StudyPlans
import io.octatec.horext.api.repository.table.SubjectRelationships
import io.octatec.horext.api.repository.table.SubjectTypes
import io.octatec.horext.api.repository.table.Subjects
import io.octatec.horext.api.util.ilike
import io.octatec.horext.api.util.unaccent
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.anyFrom
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class SubjectRepositoryImpl : SubjectRepository {
    override fun getCourseAffiliations(courseIds: Set<String>): List<CourseAffiliation> {
        if (courseIds.isEmpty()) return emptyList()

        val subject = Subjects
        val studyPlan = StudyPlans
        val speciality = OrganizationUnits
        val courseEntityIds = courseIds.map { EntityID(it, Courses) }
        val rows =
            subject
                .innerJoin(studyPlan)
                .innerJoin(speciality)
                .select(
                    subject.courseId,
                    speciality.id,
                    speciality.code,
                    speciality.name,
                    speciality.parentOrganizationId,
                ).where { subject.courseId inList courseEntityIds }
                .toList()

        val facultyIds = rows.mapNotNull { it[speciality.parentOrganizationId] }.distinct()
        val facultiesById =
            if (facultyIds.isEmpty()) {
                emptyMap()
            } else {
                speciality
                    .selectAll()
                    .where {
                        speciality.id inList facultyIds.map { EntityID(it, speciality) }
                    }.associate { row ->
                        row[speciality.id].value to
                            OrganizationUnitSummary(
                                id = row[speciality.id].value,
                                code = row[speciality.code],
                                name = row[speciality.name],
                            )
                    }
            }

        return rows
            .groupBy { it[subject.courseId].value }
            .map { (courseId, courseRows) ->
                val specialities =
                    courseRows
                        .distinctBy { it[speciality.id].value }
                        .map { row ->
                            OrganizationUnitSummary(
                                id = row[speciality.id].value,
                                code = row[speciality.code],
                                name = row[speciality.name],
                                parentId = row[speciality.parentOrganizationId],
                            )
                        }.sortedBy { it.code }
                val faculties =
                    courseRows
                        .mapNotNull { row -> row[speciality.parentOrganizationId]?.let(facultiesById::get) }
                        .distinctBy { it.id }
                        .sortedBy { it.code }
                CourseAffiliation(courseId, faculties, specialities)
            }.sortedBy { it.courseId }
    }

    override fun getAllByStudyPlanId(studyPlanId: Long): List<Subject> {
        val s = Subjects
        val c = Courses
        val st = SubjectTypes
        val sr = SubjectRelationships
        val subjects =
            s
                .innerJoin(c)
                .leftJoin(st)
                .select(s.entityColumns + c.columns + st.columns)
                .where {
                    (s.studyPlanId eq studyPlanId)
                }.orderByCurriculum(s, c)
                .map { row -> s.createEntity(row) }

        if (subjects.isEmpty()) return emptyList()

        val relationships =
            sr
                .select(sr.columns)
                .where { sr.subjectId inList subjects.map { it.id } }
                .map { row -> sr.createEntity(row) }

        val relationshipsBySubjectId = relationships.groupBy { it.subjectId }
        subjects.forEach { subject ->
            subject.relationships = relationshipsBySubjectId[subject.id].orEmpty()
        }
        return subjects
    }

    override fun getById(id: Long): Subject? {
        val s = Subjects
        val c = Courses
        val sp = StudyPlans
        val st = SubjectTypes
        val ou = OrganizationUnits
        val subject =
            s
                .innerJoin(c)
                .innerJoin(sp)
                .innerJoin(ou)
                .leftJoin(st)
                .select(s.entityColumns + c.columns + sp.entityColumns + st.columns + ou.columns)
                .where { s.id eq id }
                .map(s::createEntity)
                .singleOrNull()
                ?: return null

        subject.relationships =
            SubjectRelationships
                .select(SubjectRelationships.columns)
                .where { SubjectRelationships.subjectId eq id }
                .map(SubjectRelationships::createEntity)
        return subject
    }

    override fun getAllByIds(ids: List<Long>): List<Subject> {
        val s = Subjects
        val c = Courses
        val sp = StudyPlans
        val st = SubjectTypes
        val ou = OrganizationUnits
        return s
            .innerJoin(c)
            .innerJoin(sp)
            .innerJoin(ou)
            .leftJoin(st)
            .select(s.entityColumns + c.columns + sp.entityColumns + st.columns + ou.columns)
            .where(s.id eq anyFrom(ids))
            .orderBy(s.id to SortOrder.ASC)
            .map(s::createEntity)
    }

    private data class SubjectMatchRow(
        val subject: Subject,
        val studyPlanId: Long,
        val orgUnitId: Long,
        val orgUnitCode: String,
        val isLatestPlan: Boolean,
    )

    override fun getPageBySearchAndSpecialityIdAndHourlyLoad(
        search: String,
        specialityId: Long,
        hourlyLoadId: Long,
        offset: Int,
        limit: Int,
    ): Page<Subject> {
        val s = Subjects
        val c = Courses
        val sp = StudyPlans
        val st = SubjectTypes
        val ss = ScheduleSubjects
        val ou = OrganizationUnits
        val facultyId =
            ou
                .select(ou.parentOrganizationId)
                .where { ou.id eq specialityId }
                .firstOrNull()
                ?.get(ou.parentOrganizationId)
                ?: return Page(offset, limit, 0, content = emptyList())

        val activePlansInFaculty =
            sp
                .innerJoin(ou)
                .select(sp.id, sp.organizationUnitId, sp.fromDate, ou.code)
                .where { (ou.parentOrganizationId eq facultyId) and sp.isActive() }
                .map { row ->
                    Triple(
                        row[sp.id].value,
                        row[sp.organizationUnitId].value,
                        row[sp.fromDate],
                    )
                }

        val latestStudyPlanIds =
            activePlansInFaculty
                .groupBy { it.second }
                .mapValues { (_, plans) -> plans.maxByOrNull { it.third ?: Instant.MIN }!!.first }
                .values
                .toSet()

        val matches =
            s
                .innerJoin(c)
                .innerJoin(sp)
                .innerJoin(ou)
                .leftJoin(st)
                .select(s.entityColumns + c.columns + sp.entityColumns + st.columns + ou.columns)
                .where {
                    (ou.parentOrganizationId eq facultyId) and
                        sp.isActive() and
                        ss.existsForSubjectAndHourlyLoad(s, hourlyLoadId) and
                        c.matchesSearch(search)
                }.map { row ->
                    SubjectMatchRow(
                        subject = s.createEntity(row),
                        studyPlanId = row[sp.id].value,
                        orgUnitId = row[ou.id].value,
                        orgUnitCode = row[ou.code],
                        isLatestPlan = row[sp.id].value in latestStudyPlanIds,
                    )
                }

        val grouped =
            matches
                .groupBy { it.subject.course?.id }
                .values
                .map { courseMatches ->
                    val latestMatches = courseMatches.filter { it.isLatestPlan }
                    val relevantForCodes = if (latestMatches.isNotEmpty()) latestMatches else courseMatches

                    val specialityCodes =
                        relevantForCodes
                            .map { it.orgUnitCode }
                            .distinct()
                            .sorted()

                    val isRecommended = courseMatches.any { it.orgUnitId == specialityId }

                    val preferredMatch =
                        courseMatches.firstOrNull { it.orgUnitId == specialityId && it.isLatestPlan && (it.subject.cycle ?: 0) > 1 }
                            ?: courseMatches.firstOrNull { it.orgUnitId == specialityId && (it.subject.cycle ?: 0) > 1 }
                            ?: courseMatches.firstOrNull { it.orgUnitId == specialityId }
                            ?: latestMatches.maxByOrNull { it.subject.cycle ?: 0 }
                            ?: courseMatches.maxByOrNull { it.subject.cycle ?: 0 }
                            ?: courseMatches.first()

                    preferredMatch.subject.apply {
                        this.specialityCodes = emptyList()
                        this.recommended = isRecommended
                    }
                }.sortedWith(compareBy({ it.course?.name.orEmpty() }, { it.course?.id.orEmpty() }))
        return Page(
            offset = offset,
            limit = limit,
            totalElements = grouped.size,
            content = grouped.drop(offset).take(limit),
        )
    }

    override fun getPageBySearchAndFacultyIdAndHourlyLoad(
        search: String,
        facultyId: Long,
        hourlyLoadId: Long,
        offset: Int,
        limit: Int,
    ): Page<Subject> {
        val s = Subjects
        val c = Courses
        val sp = StudyPlans
        val st = SubjectTypes
        val ss = ScheduleSubjects
        val ou = OrganizationUnits

        val activePlansInFaculty =
            sp
                .innerJoin(ou)
                .select(sp.id, sp.organizationUnitId, sp.fromDate, ou.code)
                .where { (ou.parentOrganizationId eq facultyId) and sp.isActive() }
                .map { row ->
                    Triple(
                        row[sp.id].value,
                        row[sp.organizationUnitId].value,
                        row[sp.fromDate],
                    )
                }

        val latestStudyPlanIds =
            activePlansInFaculty
                .groupBy { it.second }
                .mapValues { (_, plans) -> plans.maxByOrNull { it.third ?: Instant.MIN }!!.first }
                .values
                .toSet()

        val matches =
            s
                .innerJoin(c)
                .innerJoin(sp)
                .innerJoin(ou)
                .leftJoin(st)
                .select(s.entityColumns + c.columns + sp.entityColumns + st.columns + ou.columns)
                .where {
                    (ou.parentOrganizationId eq facultyId) and
                        sp.isActive() and
                        ss.existsForSubjectAndHourlyLoad(s, hourlyLoadId) and
                        c.matchesSearch(search)
                }.map { row ->
                    SubjectMatchRow(
                        subject = s.createEntity(row),
                        studyPlanId = row[sp.id].value,
                        orgUnitId = row[ou.id].value,
                        orgUnitCode = row[ou.code],
                        isLatestPlan = row[sp.id].value in latestStudyPlanIds,
                    )
                }

        val grouped =
            matches
                .groupBy { it.subject.course?.id }
                .values
                .map { courseMatches ->
                    val latestMatches = courseMatches.filter { it.isLatestPlan }
                    val preferredMatch =
                        latestMatches.maxByOrNull { it.subject.cycle ?: 0 }
                            ?: courseMatches.maxByOrNull { it.subject.cycle ?: 0 }
                            ?: courseMatches.first()

                    preferredMatch.subject.apply {
                        this.specialityCodes = emptyList()
                        this.recommended = null
                    }
                }.sortedWith(compareBy({ it.course?.name.orEmpty() }, { it.course?.id.orEmpty() }))

        return Page(
            offset = offset,
            limit = limit,
            totalElements = grouped.size,
            content = grouped.drop(offset).take(limit),
        )
    }

    override fun getPageBySearchAndStudyPlanIdAndHourlyLoad(
        search: String,
        studyPlanId: Long,
        hourlyLoadId: Long,
        offset: Int,
        limit: Int,
    ): Page<Subject> {
        val s = Subjects
        val c = Courses
        val sp = StudyPlans
        val st = SubjectTypes
        val ss = ScheduleSubjects
        val ou = OrganizationUnits
        val query =
            s
                .innerJoin(c)
                .innerJoin(sp)
                .innerJoin(ou)
                .leftJoin(st)
                .select(s.entityColumns + c.columns + sp.entityColumns + st.columns + ou.columns)
                .where {
                    (sp.id eq studyPlanId) and
                        sp.isActive() and
                        ss.existsForSubjectAndHourlyLoad(s, hourlyLoadId) and
                        c.matchesSearch(search)
                }.orderByCurriculum(s, c)
        val page = query.toSubjectPage(offset, limit)
        page.content.forEach { it.recommended = true }
        return page
    }

    override fun getAllByHourlyLoadIdAndStudyPlanIdAndCycle(
        hourlyLoadId: Long,
        studyPlanId: Long,
        cycle: Int,
    ): List<Subject> {
        val s = Subjects
        val c = Courses
        val sp = StudyPlans
        val ss = ScheduleSubjects
        val st = SubjectTypes
        val ou = OrganizationUnits
        return s
            .innerJoin(c)
            .innerJoin(sp)
            .innerJoin(ou)
            .leftJoin(st)
            .select(s.entityColumns + c.columns + sp.entityColumns + st.columns + ou.columns)
            .where {
                (sp.id eq studyPlanId) and
                    sp.isActive() and
                    (s.cycle eq cycle) and
                    ss.existsForSubjectAndHourlyLoad(s, hourlyLoadId)
            }.orderByCurriculum(s, c)
            .map { row -> s.createEntity(row) }
    }

    private fun Courses.matchesSearch(search: String) = (name.unaccent() ilike ("%$search%").unaccent()) or (id ilike ("%$search%"))

    private val Subjects.entityColumns: List<Expression<*>>
        get() = listOf(id, courseId, typeId, studyPlanId, credits, cycle, createdAt, updatedAt)

    private fun StudyPlans.isActive() = (fromDate less Instant.now()) and toDate.isNull()

    private fun ScheduleSubjects.existsForSubjectAndHourlyLoad(
        subjects: Subjects,
        requestedHourlyLoadId: Long,
    ) = exists(
        select(id)
            .where {
                (subjectId eq subjects.id) and
                    (hourlyLoadId eq requestedHourlyLoadId)
            },
    )

    private fun Query.orderByStudyPlanAndCourse(
        studyPlans: StudyPlans,
        courses: Courses,
        subjects: Subjects,
    ) = orderBy(
        studyPlans.fromDate to SortOrder.DESC_NULLS_LAST,
        subjects.cycle to SortOrder.ASC_NULLS_LAST,
        courses.id to SortOrder.ASC,
        subjects.id to SortOrder.ASC,
    )

    private fun Query.orderByCurriculum(
        subjects: Subjects,
        courses: Courses,
    ) = orderBy(
        subjects.cycle to SortOrder.ASC_NULLS_LAST,
        courses.id to SortOrder.ASC,
        subjects.id to SortOrder.ASC,
    )

    private fun Query.toSubjectPage(
        offset: Int,
        limit: Int,
    ): Page<Subject> {
        val total = count().toInt()
        val content =
            limit(limit)
                .offset(offset.toLong())
                .map(Subjects::createEntity)

        return Page(offset, limit, total, content = content)
    }
}
