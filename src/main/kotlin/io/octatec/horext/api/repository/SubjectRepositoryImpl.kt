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
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
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
                .innerJoin(st)
                .select(s.columns + c.columns + st.columns)
                .where {
                    (s.studyPlanId eq studyPlanId)
                }.orderBy(c.id to SortOrder.ASC)
                .map { row -> s.createEntity(row) }
        val relationships =
            sr
                .select(sr.columns)
                .where { sr.subjectId inList subjects.map { it.id } }
                .map { row -> sr.createEntity(row) }

        subjects.forEach { subject ->
            subject.relationships = relationships.filter { it.subjectId == subject.id }
        }
        return subjects
    }

    override fun getAllBySearchAndSpecialityIdAndHourlyLoad(
        search: String,
        specialityId: Long,
        hourlyLoadId: Long,
    ): List<Subject> {
        val s = Subjects
        val c = Courses
        val sp = StudyPlans
        val ss = ScheduleSubjects
        val st = SubjectTypes
        return s
            .innerJoin(c)
            .innerJoin(sp)
            .leftJoin(st)
            .select(s.columns + c.columns + sp.columns + st.columns)
            .where {
                (sp.organizationUnitId eq specialityId) and
                    (sp.fromDate less Instant.now()) and
                    (sp.toDate.isNull()) and
                    exists(
                        ss
                            .select(ss.columns)
                            .where {
                                (ss.subjectId eq s.id) and
                                    (ss.hourlyLoadId eq hourlyLoadId)
                            },
                    ) and
                    (c.name.unaccent() ilike ("%$search%").unaccent())
            }.orderBy(
                sp.fromDate to SortOrder.DESC,
                c.id to SortOrder.ASC,
            ).map { row -> s.createEntity(row) }
    }

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
        val matches =
            s
                .innerJoin(c)
                .innerJoin(sp)
                .innerJoin(ou)
                .leftJoin(st)
                .select(s.columns + c.columns + sp.columns + st.columns + ou.columns)
                .where {
                    (ou.parentOrganizationId eq facultyId) and
                        (sp.fromDate less Instant.now()) and
                        (sp.toDate.isNull()) and
                        exists(
                            ss
                                .select(ss.id)
                                .where {
                                    (ss.subjectId eq s.id) and
                                        (ss.hourlyLoadId eq hourlyLoadId)
                                },
                        ) and
                        searchCourse(c, search)
                }.map { row -> Triple(s.createEntity(row), row[ou.id].value, row[ou.code]) }

        val grouped =
            matches
                .groupBy { it.first.course?.id }
                .values
                .map { courseMatches ->
                    val preferred = courseMatches.firstOrNull { it.second == specialityId } ?: courseMatches.first()
                    preferred.first.apply {
                        specialityCodes = courseMatches.map { it.third }.distinct().sorted()
                        recommended = courseMatches.any { it.second == specialityId }
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
        val query =
            s
                .innerJoin(c)
                .innerJoin(sp)
                .innerJoin(ou)
                .leftJoin(st)
                .select(s.columns + c.columns + sp.columns + st.columns + ou.columns)
                .where {
                    (ou.parentOrganizationId eq facultyId) and
                        (sp.fromDate less Instant.now()) and
                        (sp.toDate.isNull()) and
                        exists(
                            ss
                                .select(ss.id)
                                .where {
                                    (ss.subjectId eq s.id) and
                                        (ss.hourlyLoadId eq hourlyLoadId)
                                },
                        ) and
                        searchCourse(c, search)
                }.orderBy(
                    sp.fromDate to SortOrder.DESC,
                    c.id to SortOrder.ASC,
                )
        val queryResultCount = query.count()
        val queryResult = query.limit(limit).offset(offset.toLong())
        val list = queryResult.map { row -> s.createEntity(row) }
        return Page(offset, limit, queryResultCount.toInt(), content = list)
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
        val query =
            s
                .innerJoin(c)
                .innerJoin(sp)
                .leftJoin(st)
                .select(s.columns + c.columns + sp.columns + st.columns)
                .where {
                    (sp.id eq studyPlanId) and
                        (sp.fromDate less Instant.now()) and
                        (sp.toDate.isNull()) and
                        exists(
                            ss
                                .select(ss.id)
                                .where {
                                    (ss.subjectId eq s.id) and
                                        (ss.hourlyLoadId eq hourlyLoadId)
                                },
                        ) and
                        searchCourse(c, search)
                }.orderBy(c.id to SortOrder.ASC)
        val queryResultCount = query.count()
        val queryResult = query.limit(limit).offset(offset.toLong())
        val list = queryResult.map { row -> s.createEntity(row) }
        return Page(offset, limit, queryResultCount.toInt(), content = list)
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
        return s
            .innerJoin(c)
            .innerJoin(sp)
            .leftJoin(st)
            .select(s.columns + c.columns + sp.columns + st.columns)
            .where {
                (sp.id eq studyPlanId) and
                    (sp.fromDate less Instant.now()) and
                    (sp.toDate.isNull()) and
                    (s.cycle eq cycle) and
                    exists(
                        ss
                            .select(ss.columns)
                            .where {
                                (ss.subjectId eq s.id) and
                                    (ss.hourlyLoadId eq hourlyLoadId)
                            },
                    )
            }.orderBy(c.id to SortOrder.ASC)
            .map { row -> s.createEntity(row) }
    }

    private fun searchCourse(
        c: Courses,
        search: String,
    ) = (c.name.unaccent() ilike ("%$search%").unaccent()) or (c.id ilike ("%$search%"))
}
