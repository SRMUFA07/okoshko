package com.okoshko.service;

import com.okoshko.dto.TeacherWorkspaceResponse;
import com.okoshko.dto.TeacherWorkspaceResponse.GroupResponse;
import com.okoshko.dto.GroupLessonLinkResponse;
import com.okoshko.dto.GroupHomeworkResponse;
import com.okoshko.entity.GroupHomeworkEntity;
import com.okoshko.entity.TeacherWorkspaceGroupEntity;
import com.okoshko.repository.TeacherWorkspaceRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TeacherWorkspaceService {

	private final TeacherWorkspaceRepository repository;
	private final ZoneId schoolTimeZone;

	public TeacherWorkspaceService(
			TeacherWorkspaceRepository repository,
			@org.springframework.beans.factory.annotation.Value("${app.school.time-zone}") String schoolTimeZone) {
		this.repository = repository;
		this.schoolTimeZone = ZoneId.of(schoolTimeZone);
	}

	public TeacherWorkspaceResponse getWorkspace(long telegramId) {
		long teacherId = requireTeacherId(telegramId);
		List<GroupResponse> groups = repository.findGroupsByTeacherId(teacherId).stream()
				.map(TeacherWorkspaceService::toResponse)
				.toList();
		return new TeacherWorkspaceResponse(groups);
	}

	public GroupLessonLinkResponse updateLessonLink(long telegramId, long groupId, String lessonLink) {
		long teacherId = requireTeacherId(telegramId);
		validateLessonLink(lessonLink);
		if (!repository.updateGroupLessonLink(teacherId, groupId, lessonLink)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
		}
		return new GroupLessonLinkResponse(groupId, lessonLink);
	}

	@Transactional
	public GroupHomeworkResponse saveHomework(
			long telegramId, long groupId, String name, String description, LocalDateTime dueDate) {
		long teacherId = requireTeacherId(telegramId);
		if (name == null || name.isBlank() || name.length() > 200
				|| description == null || description.isBlank() || dueDate == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Homework name, description and due date are required");
		}
		List<ZoneOffset> offsets = schoolTimeZone.getRules().getValidOffsets(dueDate);
		if (offsets.size() != 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Homework due date is invalid in school time zone");
		}
		OffsetDateTime dueAt = OffsetDateTime.of(dueDate, offsets.getFirst());
		GroupHomeworkEntity saved = repository.upsertGroupHomework(
				teacherId, groupId, name.trim(), description.trim(), dueAt)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
		return new GroupHomeworkResponse(
				saved.groupId(), saved.id(), saved.name(), saved.description(), saved.dueDate());
	}

	private long requireTeacherId(long telegramId) {
		return repository.findTeacherIdByTelegramId(telegramId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Teacher role is required"));
	}

	private static void validateLessonLink(String value) {
		if (value == null || value.isBlank() || value.length() > 2048) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lesson link is required");
		}
		try {
			URI uri = URI.create(value.trim());
			if (!uri.isAbsolute() || uri.getHost() == null
					|| !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
					|| uri.getUserInfo() != null) {
				throw new IllegalArgumentException();
			}
		}
		catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lesson link must be an HTTP(S) URL");
		}
	}

	private static GroupResponse toResponse(TeacherWorkspaceGroupEntity group) {
		return new GroupResponse(
				group.id(), group.name(), group.englishLevel(), group.schedule(), group.lessonLink(),
				group.studentCount(),
				group.lessons().stream()
						.map(lesson -> new TeacherWorkspaceResponse.LessonResponse(
								lesson.id(), lesson.date(), lesson.time()))
						.toList(),
				group.homework().stream()
						.map(homework -> new TeacherWorkspaceResponse.HomeworkResponse(
								homework.id(), homework.name(), homework.description(), homework.dueDate()))
						.toList());
	}
}
