package com.shoppingdistrict.microservices.userservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shoppingdistrict.microservices.model.model.Subscription;
import com.shoppingdistrict.microservices.model.model.Users;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

	List<Subscription> findByEmail(String email);
	
}
