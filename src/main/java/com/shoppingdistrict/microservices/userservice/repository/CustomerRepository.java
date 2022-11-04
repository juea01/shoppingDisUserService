package com.shoppingdistrict.microservices.userservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shoppingdistrict.microservices.model.model.Users;

public interface CustomerRepository extends JpaRepository<Users, Integer> {

	List<Users> findByUsername(String username);
}
