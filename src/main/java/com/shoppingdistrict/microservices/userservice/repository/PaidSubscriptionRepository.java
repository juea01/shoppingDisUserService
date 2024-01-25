package com.shoppingdistrict.microservices.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shoppingdistrict.microservices.model.model.PaidSubscription;

public interface PaidSubscriptionRepository extends JpaRepository<PaidSubscription, Integer>{

}
