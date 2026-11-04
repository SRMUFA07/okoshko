package com.okoshko.repository;

import com.okoshko.entity.AdminRequestEntity;
import com.okoshko.entity.CreatedRequestEntity;
import com.okoshko.entity.StudentRequestEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestRepository {
	Optional<Long> findStudentIdByTelegramId(long telegramId);

	boolean allGroupsExist(List<Long> groupIds);

	CreatedRequestEntity create(long studentId, OffsetDateTime desiredTime, List<Long> groupIds, UUID idempotencyKey);

	List<StudentRequestEntity> findAllForStudent(long studentId);

	boolean isAdmin(long telegramId);

	List<AdminRequestEntity> findAllForAdmin();

	boolean updatePendingStatus(long requestId, String status);

	boolean exists(long requestId);
}
