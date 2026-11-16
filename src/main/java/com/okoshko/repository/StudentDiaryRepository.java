package com.okoshko.repository;

import com.okoshko.dto.StudentDiaryResponse.GroupResponse;
import java.util.List;
import java.util.Optional;

public interface StudentDiaryRepository {
	Optional<Long> findStudentIdByTelegramId(long telegramId);
	List<GroupResponse> findGroupsByStudentId(long studentId);
}
