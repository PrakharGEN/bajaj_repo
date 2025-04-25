package com.bajaj.healthchallenge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

@Component
public class StartupRunner implements CommandLineRunner {

    private static final String INIT_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";

    @Override
    public void run(String... args) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper mapper = new ObjectMapper();

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("name", "John Doe");
        requestBody.put("regNo", "REG12347");
        requestBody.put("email", "john@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(INIT_URL, request, String.class);
        JsonNode root = mapper.readTree(response.getBody());

        String webhookUrl = root.get("webhook").asText();
        String accessToken = root.get("accessToken").asText();
        JsonNode data = root.get("data");

        List<List<Integer>> outcome;

        String regNo = requestBody.get("regNo");
        int lastDigit = Character.getNumericValue(regNo.charAt(regNo.length() - 1));

        if (lastDigit % 2 == 1) {
            outcome = solveMutualFollowers(data.get("users"));
        } else {
            outcome = solveNthLevelFollowers(data);
        }

        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("regNo", regNo);
        finalResult.put("outcome", outcome);

        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setContentType(MediaType.APPLICATION_JSON);
        postHeaders.set("Authorization", accessToken);
        HttpEntity<Map<String, Object>> postRequest = new HttpEntity<>(finalResult, postHeaders);

        boolean success = false;
        int retries = 0;
        while (!success && retries < 4) {
            try {
                restTemplate.postForEntity(webhookUrl, postRequest, String.class);
                success = true;
            } catch (Exception ex) {
                retries++;
                Thread.sleep(1000);
            }
        }
    }

    private List<List<Integer>> solveMutualFollowers(JsonNode usersNode) {
        Map<Integer, Set<Integer>> followsMap = new HashMap<>();
        for (JsonNode user : usersNode) {
            int id = user.get("id").asInt();
            Set<Integer> follows = new HashSet<>();
            for (JsonNode f : user.get("follows")) {
                follows.add(f.asInt());
            }
            followsMap.put(id, follows);
        }

        List<List<Integer>> mutuals = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (int id : followsMap.keySet()) {
            for (int followee : followsMap.get(id)) {
                if (followsMap.containsKey(followee) && followsMap.get(followee).contains(id)) {
                    String key = Math.min(id, followee) + ":" + Math.max(id, followee);
                    if (!visited.contains(key)) {
                        mutuals.add(Arrays.asList(Math.min(id, followee), Math.max(id, followee)));
                        visited.add(key);
                    }
                }
            }
        }
        return mutuals;
    }

    private List<List<Integer>> solveNthLevelFollowers(JsonNode data) {
        int n = data.get("n").asInt();
        int findId = data.get("findId").asInt();
        JsonNode usersNode = data.get("users");

        Map<Integer, List<Integer>> graph = new HashMap<>();
        for (JsonNode user : usersNode) {
            int id = user.get("id").asInt();
            List<Integer> follows = new ArrayList<>();
            for (JsonNode f : user.get("follows")) {
                follows.add(f.asInt());
            }
            graph.put(id, follows);
        }

        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(findId);
        visited.add(findId);

        int level = 0;
        while (!queue.isEmpty() && level < n) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                int current = queue.poll();
                for (int neighbor : graph.getOrDefault(current, Collections.emptyList())) {
                    if (!visited.contains(neighbor)) {
                        queue.add(neighbor);
                        visited.add(neighbor);
                    }
                }
            }
            level++;
        }

        List<List<Integer>> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            result.add(Collections.singletonList(queue.poll()));
        }

        return result;
    }
}