package com.bajar.saman.repository;

import com.bajar.saman.entity.UserRole;
import com.bajar.saman.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUser_Id(UUID userId);
}