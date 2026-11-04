package com.okoshko.repository;

import com.okoshko.entity.TeacherEntity;
import java.util.List;
import java.util.Optional;

public interface TeacherRepository {
	List<TeacherEntity> findAll();

	Optional<TeacherEntity> findById(long id);
}
