package io.octatec.horext.api.service

import io.octatec.horext.api.domain.Subject
import io.octatec.horext.api.dto.CourseAffiliation
import io.octatec.horext.api.dto.OrganizationUnitSummary
import io.octatec.horext.api.exception.ResourceNotFoundException
import io.octatec.horext.api.repository.SubjectRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class SubjectServiceImplTest {
    @Mock
    private lateinit var subjectRepository: SubjectRepository

    private lateinit var service: SubjectServiceImpl

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = SubjectServiceImpl(subjectRepository)
    }

    @Test
    fun getCourseAffiliations_returnsRepositoryResult() {
        val courseIds = setOf("BIC01")
        val expected =
            listOf(
                CourseAffiliation(
                    courseId = "BIC01",
                    faculties = listOf(OrganizationUnitSummary(1, "FIIS", "FIIS")),
                    specialities = listOf(OrganizationUnitSummary(2, "I1", "Sistemas")),
                ),
            )
        `when`(subjectRepository.getCourseAffiliations(courseIds)).thenReturn(expected)

        assertEquals(expected, service.getCourseAffiliations(courseIds))
        verify(subjectRepository).getCourseAffiliations(courseIds)
    }

    @Test
    fun getAllByStudyPlanId_returnsRepositoryResult() {
        val studyPlanId = 1L
        val expected = listOf(Subject(id = 100L))
        `when`(subjectRepository.getAllByStudyPlanId(studyPlanId)).thenReturn(expected)

        val result = service.getAllByStudyPlanId(studyPlanId)

        assertEquals(expected, result)
        verify(subjectRepository).getAllByStudyPlanId(studyPlanId)
    }

    @Test
    fun getAllByStudyPlanId_propagatesRepositoryException() {
        val studyPlanId = 1L
        `when`(subjectRepository.getAllByStudyPlanId(studyPlanId)).thenThrow(RuntimeException("db"))

        assertThrows(RuntimeException::class.java) {
            service.getAllByStudyPlanId(studyPlanId)
        }
        verify(subjectRepository).getAllByStudyPlanId(studyPlanId)
    }

    @Test
    fun getById_returnsRepositoryResult() {
        val subject = Subject(id = 1L)
        `when`(subjectRepository.getById(subject.id)).thenReturn(subject)

        assertEquals(subject, service.getById(subject.id))
        verify(subjectRepository).getById(subject.id)
    }

    @Test
    fun getById_throwsNotFoundWhenSubjectDoesNotExist() {
        val subjectId = 1L
        `when`(subjectRepository.getById(subjectId)).thenReturn(null)

        assertThrows(ResourceNotFoundException::class.java) { service.getById(subjectId) }
        verify(subjectRepository).getById(subjectId)
    }

    @Test
    fun getAllByIds_returnsRepositoryResult() {
        val ids = listOf(3L, 1L)
        val expected = listOf(Subject(id = 1L), Subject(id = 3L))
        `when`(subjectRepository.getAllByIds(ids)).thenReturn(expected)

        val result = service.getAllByIds(ids)

        assertEquals(expected, result)
        verify(subjectRepository).getAllByIds(ids)
    }

    @Test
    fun getPageBySearchAndSpecialityIdAndHourlyLoad_delegatesToRepository() {
        val subject = Subject(id = 1L).apply { recommended = true }
        val page =
            io.octatec.horext.api.dto
                .Page(offset = 0, limit = 10, totalElements = 1, content = listOf(subject))
        `when`(subjectRepository.getPageBySearchAndSpecialityIdAndHourlyLoad("SW", 2L, 1L, 0, 10))
            .thenReturn(page)

        val result = service.getPageBySearchAndSpecialityIdAndHourlyLoad("SW", 2L, 1L, 0, 10)

        assertEquals(page, result)
        verify(subjectRepository).getPageBySearchAndSpecialityIdAndHourlyLoad("SW", 2L, 1L, 0, 10)
    }

    @Test
    fun getPageBySearchAndStudyPlanIdAndHourlyLoad_delegatesToRepository() {
        val subject = Subject(id = 1L).apply { recommended = true }
        val page =
            io.octatec.horext.api.dto
                .Page(offset = 0, limit = 10, totalElements = 1, content = listOf(subject))
        `when`(subjectRepository.getPageBySearchAndStudyPlanIdAndHourlyLoad("SW", 4L, 1L, 0, 10))
            .thenReturn(page)

        val result = service.getPageBySearchAndStudyPlanIdAndHourlyLoad("SW", 4L, 1L, 0, 10)

        assertEquals(page, result)
        verify(subjectRepository).getPageBySearchAndStudyPlanIdAndHourlyLoad("SW", 4L, 1L, 0, 10)
    }
}
