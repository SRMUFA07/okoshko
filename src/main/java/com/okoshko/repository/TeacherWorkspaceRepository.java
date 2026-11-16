package com.okoshko.repository;

import com.okoshko.entity.TeacherWorkspaceGroupEntity;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import com.okoshko.entity.GroupHomeworkEntity;

public interface TeacherWorkspaceRepository {

	Optional<Long> findTeacherIdByTelegramId(long telegramId);

	List<TeacherWorkspaceGroupEntity> findGroupsByTeacherId(long teacherId);

	boolean updateGroupLessonLink(long teacherId, long groupId, String lessonLink);

	Optional<GroupHomeworkEntity> upsertGroupHomework(
			long teacherId, long groupId, String name, String description, OffsetDateTime dueDate);
}
