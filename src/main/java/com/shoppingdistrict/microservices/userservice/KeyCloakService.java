package com.shoppingdistrict.microservices.userservice;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class KeyCloakService {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Value("${keycloak.management.id}")
	private String keycloakManagementClientId;

	@Value("${keycloak.management.secret}")
	private String keycloakManagementClientSecret;

	@Value("${keycloak.token.url}")
	private String keycloakManagementTokenUrl;

	@Value("${keycloak.user.evict.url}")
	private String keycloakUserEvictUrl;

	@Value("${keycloak.management.scope}")
	private String keycloakManagementScope;

	@Value("${keycloak.management.grant_type}")
	private String keycloakManagementGrantType;

	@Value("${keycloak.user.getid.byusername.url}")
	private String keycloakUserIdByUserNameUrl;

	public String getKeyCloakManagementAccessToken() throws Exception {
		logger.info("Entry to getKeyCloakManagementAccessToken");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		String requestBody = "client_id=" + keycloakManagementClientId + "&client_secret="
				+ keycloakManagementClientSecret + "&scope=" + keycloakManagementScope + "&grant_type="
				+ keycloakManagementGrantType;

		HttpEntity<String> reqEntity = new HttpEntity<>(requestBody, headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> responseEntity = restTemplate.exchange(keycloakManagementTokenUrl, HttpMethod.POST,
				reqEntity, String.class);

		if (responseEntity.getStatusCode().is2xxSuccessful()) {
			logger.info("Token Code received. Exiting from getKeyCloakManagementAccessToken");
			JSONObject jsonObject = new JSONObject(responseEntity.getBody().toString());
			return jsonObject.getString("access_token");
		} else {
			logger.info("Error requesting Token Code {}");
			throw new Exception(" Error requesting Token for management");
		}
	}

	public boolean evictUserFromKeycloakCache(String accessToken, String userId) throws Exception {
		logger.info("Entry to evictUserFromKeycloakCache for userId {}", userId);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.set("Authorization", "Bearer " + accessToken);

		String requestBody = "userId=" + userId;

		HttpEntity<String> reqEntity = new HttpEntity<>(requestBody, headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> responseEntity = restTemplate.exchange(keycloakUserEvictUrl, HttpMethod.POST, reqEntity,
				String.class);

		if (responseEntity.getStatusCode().is2xxSuccessful()) {
			logger.info("User {} evicted successfully from keycloak cache, exiting from evictUserFromKeycloakCache",
					userId);
			return true;
		} else {
			logger.info("Error occurred when evicting user from keycloak cache");
			throw new Exception("Error occurred when evicting user from keycloak cache");
		}
	}

	public String getKeycloakUserIdByUsername(String username, String accessToken) {
		logger.info("Entry to getKeycloakUserIdByUsername for username {}", username);
		String url = keycloakUserIdByUserNameUrl.replace("{username}", username);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.set("Authorization", "Bearer " + accessToken);
		HttpEntity<String> reqEntity = new HttpEntity<>(null, headers);

		RestTemplate restTemplate = new RestTemplate();
		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, reqEntity, String.class);

			logger.info("Status code {}", response.getStatusCode());
			String userId = extractKeycloakUserId(response.getBody());

			logger.info("User id returned by keycloak management service for username {} is {}", username,
					response.getBody());
			logger.info("Exiting from getKeycloakUserIdByUsername");
			return userId;

		} catch (HttpClientErrorException exception) {

			logger.info("No user Id found with given username {} and status code returned by keycloak is {}", username,
					exception.getRawStatusCode());
			return null;
		}

	}

	private String extractKeycloakUserId(String body) {
		// TODO Auto-generated method stub
		logger.info("Key cloak user id {}", body);
		return body;
	}

}
