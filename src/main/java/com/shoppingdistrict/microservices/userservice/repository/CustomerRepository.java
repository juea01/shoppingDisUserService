package com.shoppingdistrict.microservices.userservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.shoppingdistrict.microservices.model.model.Users;

public interface CustomerRepository extends JpaRepository<Users, Integer> {

	List<Users> findByUsername(String username);
	List<Users> findByEmail(String email);
	
	@Modifying
	@Query("UPDATE Users u SET u.role = ?2 WHERE u.id = ?1")
	void updateUserRoleById(int id, String role);
}
