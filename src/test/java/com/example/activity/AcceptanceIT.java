package com.example.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验收集成测试（Failsafe：`mvn verify` 运行，需真实 MySQL + Redis）。
 * 覆盖任务书「公开的关键验收」：50 用户抢 10 名额、同键并发 20、异键并发 20，
 * 以及重放、冲突、跨用户隔离、满额后重放、失败请求不绑定键、分页校验。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcceptanceIT {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    final ObjectMapper om = new ObjectMapper();
    final HttpClient client = HttpClient.newBuilder().build();

    String base() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void resetDb() {
        jdbcTemplate.execute("DELETE FROM registration");
        jdbcTemplate.execute("UPDATE activity SET remaining_quota = total_quota");
        jdbcTemplate.execute("UPDATE activity SET status = 'CLOSED' WHERE id = 1004");
        jdbcTemplate.execute("UPDATE activity SET status = 'OPEN' WHERE id <> 1004");
    }

    // ---- HTTP helpers ----

    HttpResponse<String> post(long userId, long activityId, String requestId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api/registrations"))
                .header("X-User-Id", String.valueOf(userId))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"activityId\":" + activityId + ",\"requestId\":\"" + requestId + "\"}"))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> getReg(long userId, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api/registrations" + path))
                .header("X-User-Id", String.valueOf(userId))
                .GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> getAct(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/api" + path)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    List<HttpResponse<String>> fireConcurrent(int n, IntFunction<HttpRequest> builder) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return client.send(builder.apply(idx), HttpResponse.BodyHandlers.ofString());
            }));
        }
        ready.await(30, TimeUnit.SECONDS);
        go.countDown();
        List<HttpResponse<String>> results = new ArrayList<>();
        for (Future<HttpResponse<String>> f : futures) {
            results.add(f.get(90, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    JsonNode json(HttpResponse<String> r) throws Exception {
        return om.readTree(r.body());
    }

    static String code(JsonNode node) {
        return node.path("code").asText();
    }

    static String dataId(JsonNode node) {
        return node.path("data").path("id").asText();
    }

    // ---- 基础闭环：正常报名 + 成功重放 ----

    @Test
    void register_thenReplay_returnsSameRecord() throws Exception {
        HttpResponse<String> first = post(1101, 1001, "signup_001");
        assertEquals(201, first.statusCode());
        JsonNode firstJson = json(first);
        String firstId = dataId(firstJson);
        assertEquals("REGISTERED", firstJson.path("data").path("status").asText());

        HttpResponse<String> replay = post(1101, 1001, "signup_001");
        assertEquals(200, replay.statusCode());
        assertEquals(firstId, dataId(json(replay)));

        // 名额只扣一次
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_quota FROM activity WHERE id=1001", Integer.class);
        assertEquals(4, remaining);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id=1101 AND activity_id=1001", Integer.class);
        assertEquals(1, count);
    }

    // ---- 身份与参数校验 ----

    @Test
    void validation_missingHeader_badKey_badPage() throws Exception {
        // 缺失 X-User-Id
        HttpRequest noHeader = HttpRequest.newBuilder(URI.create(base() + "/api/registrations"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"activityId\":1001,\"requestId\":\"k1\"}"))
                .build();
        HttpResponse<String> r1 = client.send(noHeader, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, r1.statusCode());
        assertEquals("INVALID_ARGUMENT", code(json(r1)));

        // 非法 requestId
        assertEquals(400, post(1101, 1001, "bad key!").statusCode());

        // 非正数 userId
        assertEquals(400, post(0, 1001, "k1").statusCode());

        // 非法分页
        assertEquals(400, getAct("/activities?page=0&size=10").statusCode());
        assertEquals(400, getAct("/activities?page=1&size=101").statusCode());
    }

    // ---- 活动列表与详情 ----

    @Test
    void activityList_orderedAndDetailCorrect() throws Exception {
        HttpResponse<String> list = getAct("/activities?page=1&size=10");
        assertEquals(200, list.statusCode());
        JsonNode items = json(list).path("data").path("items");
        assertEquals(7, items.size());
        assertEquals(1001, items.get(0).path("id").asInt());
        assertEquals(1007, items.get(6).path("id").asInt());

        // 详情：零名额活动 OPEN 且剩余 0
        HttpResponse<String> zero = getAct("/activities/1003");
        assertEquals(200, zero.statusCode());
        assertEquals(0, json(zero).path("data").path("remainingQuota").asInt());
        assertEquals("OPEN", json(zero).path("data").path("status").asText());

        // 已关闭活动
        assertEquals("CLOSED", json(getAct("/activities/1004")).path("data").path("status").asText());

        // 不存在
        HttpResponse<String> notFound = getAct("/activities/9999");
        assertEquals(404, notFound.statusCode());
        assertEquals("ACTIVITY_NOT_FOUND", code(json(notFound)));
    }

    // ---- 公开关键验收 1：50 用户抢 10 名额 ----

    @Test
    void quota_50UsersCompeteFor10() throws Exception {
        List<HttpResponse<String>> results = fireConcurrent(50, i -> {
            try {
                return HttpRequest.newBuilder(URI.create(base() + "/api/registrations"))
                        .header("X-User-Id", String.valueOf(10000L + i))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"activityId\":1002,\"requestId\":\"k50_" + i + "\"}"))
                        .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        long created = results.stream().filter(r -> r.statusCode() == 201).count();
        long soldOut = results.stream()
                .filter(r -> r.statusCode() == 409)
                .filter(r -> {
                    try { return "SOLD_OUT".equals(code(json(r))); } catch (Exception e) { return false; }
                }).count();
        assertEquals(10, created, "10 次首次成功");
        assertEquals(40, soldOut, "40 次满额");

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_quota FROM activity WHERE id=1002", Integer.class);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE activity_id=1002", Integer.class);
        assertEquals(0, remaining, "最终剩余 0");
        assertEquals(10, count, "最终 10 条记录");
    }

    // ---- 公开关键验收 2：同用户同键并发 20 次 ----

    @Test
    void sameKey_20Concurrent_convergeToOne() throws Exception {
        List<HttpResponse<String>> results = fireConcurrent(20, i -> {
            try {
                return HttpRequest.newBuilder(URI.create(base() + "/api/registrations"))
                        .header("X-User-Id", "2001")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"activityId\":1007,\"requestId\":\"same_key\"}"))
                        .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        long created = results.stream().filter(r -> r.statusCode() == 201).count();
        long replay = results.stream().filter(r -> r.statusCode() == 200).count();
        assertEquals(1, created, "1 次新建");
        assertEquals(19, replay, "19 次重放");

        Set<String> ids = new HashSet<>();
        for (HttpResponse<String> r : results) {
            if (r.statusCode() == 200 || r.statusCode() == 201) {
                ids.add(dataId(json(r)));
            }
        }
        assertEquals(1, ids.size(), "返回同一报名 ID");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id=2001 AND activity_id=1007", Integer.class);
        assertEquals(1, count, "只落库 1 条");
    }

    // ---- 公开关键验收 3：同用户不同键并发 20 次 ----

    @Test
    void differentKeys_20Concurrent_onlyOneSucceeds() throws Exception {
        List<HttpResponse<String>> results = fireConcurrent(20, i -> {
            try {
                return HttpRequest.newBuilder(URI.create(base() + "/api/registrations"))
                        .header("X-User-Id", "2002")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"activityId\":1006,\"requestId\":\"dk_" + i + "\"}"))
                        .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        long created = results.stream().filter(r -> r.statusCode() == 201).count();
        long already = results.stream()
                .filter(r -> r.statusCode() == 409)
                .filter(r -> {
                    try { return "ALREADY_REGISTERED".equals(code(json(r))); } catch (Exception e) { return false; }
                }).count();
        assertEquals(1, created, "只允许 1 次成功");
        assertEquals(19, already, "其余 19 次为业务去重");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id=2002 AND activity_id=1006", Integer.class);
        assertEquals(1, count);
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_quota FROM activity WHERE id=1006", Integer.class);
        assertEquals(4, remaining, "名额只扣 1");
    }

    // ---- 参数冲突 / 业务去重 / 满额后重放 / 失败不绑定键 ----

    @Test
    void idempotencyConflict_and_businessDedup() throws Exception {
        assertEquals(201, post(4001, 1001, "shared").statusCode());
        // 同键用于另一活动 → 参数冲突
        HttpResponse<String> conflict = post(4001, 1005, "shared");
        assertEquals(409, conflict.statusCode());
        assertEquals("IDEMPOTENCY_CONFLICT", code(json(conflict)));
        // 换键重复报名同一活动 → 业务去重
        HttpResponse<String> dup = post(4001, 1001, "another_key");
        assertEquals(409, dup.statusCode());
        assertEquals("ALREADY_REGISTERED", code(json(dup)));
    }

    @Test
    void replayAfterFull_stillReturnsOriginal() throws Exception {
        // 用 5 个不同用户填满活动 1001（名额 5）
        for (int i = 0; i < 5; i++) {
            assertEquals(201, post(10000L + i, 1001, "fill_" + i).statusCode());
        }
        // 活动已满，新用户报名 → 满额
        HttpResponse<String> soldOut = post(99999, 1001, "late_1");
        assertEquals(409, soldOut.statusCode());
        assertEquals("SOLD_OUT", code(json(soldOut)));

        // 已成功用户重放，即使满额也应返回原记录（200）
        HttpResponse<String> replay = post(10000, 1001, "fill_0");
        assertEquals(200, replay.statusCode());
        assertEquals(200, post(10004, 1001, "fill_4").statusCode());
    }

    @Test
    void failedRequest_doesNotBindKey() throws Exception {
        // 报名已关闭活动失败
        HttpResponse<String> closed = post(3001, 1004, "temp_key");
        assertEquals(409, closed.statusCode());
        assertEquals("ACTIVITY_CLOSED", code(json(closed)));

        // 报名零名额活动失败
        HttpResponse<String> soldOut = post(3001, 1003, "temp_key2");
        assertEquals(409, soldOut.statusCode());
        assertEquals("SOLD_OUT", code(json(soldOut)));

        // 同一键可用于开放活动（失败请求未占用键）
        assertEquals(201, post(3001, 1001, "temp_key").statusCode());
        assertEquals(201, post(3001, 1002, "temp_key2").statusCode());
    }

    @Test
    void crossUserIsolation_hiddenOr404() throws Exception {
        HttpResponse<String> created = post(5001, 1001, "iso_1");
        assertEquals(201, created.statusCode());
        String id = dataId(json(created));

        assertEquals(200, getReg(5001, "/" + id).statusCode());
        HttpResponse<String> other = getReg(5002, "/" + id);
        assertEquals(404, other.statusCode());
        assertEquals("REGISTRATION_NOT_FOUND", code(json(other)));

        // 列表隔离：5002 看不到 5001 的记录
        JsonNode list2 = json(getReg(5002, "?page=1&size=10"));
        assertEquals(0, list2.path("data").path("total").asLong());
        JsonNode list1 = json(getReg(5001, "?page=1&size=10"));
        assertEquals(1, list1.path("data").path("total").asLong());
        assertTrue(list1.path("data").path("items").get(0).path("userId").asLong() == 5001L);
    }
}
