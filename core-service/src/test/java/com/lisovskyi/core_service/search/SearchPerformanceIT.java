package com.lisovskyi.core_service.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.search.dto.response.SearchResponse;
import com.sentio.shared.entity.id.organization.OrganizationId;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// SEN-22 AC: "Відповідь за <=200 мс на 10 000 клієнтів" - не перевірялось раніше жодним тестом,
// EXPLAIN на голому Postgres (без Hibernate/MapStruct/HTTP-шару) доводив лише що план запиту
// коректний, а не що сам SearchService укладається в бюджет. Тут - наскрізний прогін через
// реальний Spring-контекст і Testcontainers Postgres із 10к рядками клієнтів.
//
// Дані сіються сирим batch-insert через JdbcTemplate, а не Client.builder()+repository.save()
// в циклі: 10к окремих Hibernate persist+flush - це вже саме собою секунди в setUp, які тут
// не мета тесту (перевіряємо бюджет пошукового запиту, не швидкість сидування).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SearchPerformanceIT {

    private static final Logger log = LoggerFactory.getLogger(SearchPerformanceIT.class);

    private static final long ORGANIZATION_ID = 9_100_001L;
    private static final int CLIENT_COUNT = 10_000;
    private static final long RESPONSE_BUDGET_MS = 200;

    // ті самі перевірені по контрольним алгоритмам значення, що і в SearchServiceTest
    private static final String TARGET_RNOKPP = "1234567899";
    private static final String TARGET_EDRPOU = "12345678";
    private static final String TARGET_LAST_NAME = "Штепенко";

    private static final String[] NOISE_LAST_NAMES = {
        "Іваненко", "Петренко", "Коваленко", "Шевченко", "Бондаренко", "Мельник", "Ткаченко", "Кравченко"
    };
    private static final String[] NOISE_FIRST_NAMES = {"Олена", "Іван", "Марія", "Андрій", "Наталія", "Сергій"};

    @Autowired
    private SearchService searchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    // Порядок стовпців для batch-insert нижче - усі NOT NULL/CHECK-обмеження з
    // V2__clients.sql і V23__clients_extend_type_check_constraints.sql мають бути
    // задоволені для кожного рядка (адреса, контакт, і по типу - ПІБ+д.н.+РНОКПП/паспорт
    // для фізособи, компанія+ЄДРПОУ+директор+контактна особа для юрособи), інакше
    // batch insert падає з DataIntegrityViolationException.
    private static final String INSERT_SQL =
            """
            INSERT INTO core.clients
                (id, organization_id, type, last_name, first_name, birth_date, rnokpp, edrpou,
                 passport, company_name, director_name, contact_person_name, address, email, created_by)
            VALUES (?, ?, ?::core.client_type, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            """;

    private Object[] individualRow(long id, String lastName, String firstName, String rnokpp, String passport) {
        return new Object[] {
            id,
            ORGANIZATION_ID,
            "INDIVIDUAL",
            lastName,
            firstName,
            java.sql.Date.valueOf("1990-01-01"),
            rnokpp,
            null,
            passport,
            null,
            null,
            null,
            "м. Київ, вул. Тестова, 1",
            "client" + id + "@example.test",
        };
    }

    private Object[] companyRow(long id, String edrpou) {
        return new Object[] {
            id,
            ORGANIZATION_ID,
            "COMPANY",
            null,
            null,
            null,
            null,
            edrpou,
            null,
            "ТОВ Тестова Компанія",
            "Директор Тестенко",
            "Контакт Тестенко",
            "м. Київ, вул. Тестова, 2",
            "company" + id + "@example.test",
        };
    }

    @BeforeEach
    void seedTenThousandClients() {
        List<Object[]> rows = new ArrayList<>(CLIENT_COUNT);
        long id = 9_200_000L;

        // цільовий фізособа-клієнт - унікальне прізвище (нечіткий пошук) + РНОКПП (точний)
        rows.add(individualRow(id++, TARGET_LAST_NAME, "Тест", TARGET_RNOKPP, null));
        // цільовий юрособа-клієнт - ЄДРПОУ (точний збіг)
        rows.add(companyRow(id++, TARGET_EDRPOU));

        for (int i = 0; i < CLIENT_COUNT - 2; i++) {
            rows.add(individualRow(
                    id++,
                    NOISE_LAST_NAMES[i % NOISE_LAST_NAMES.length],
                    NOISE_FIRST_NAMES[i % NOISE_FIRST_NAMES.length],
                    null,
                    "PP" + id));
        }

        jdbcTemplate.batchUpdate(INSERT_SQL, rows);
        entityManager.flush();
        entityManager.clear();
    }

    private SearchResponse timedSearch(String label, String query) {
        // холостий прогін - виключає JIT/кеш-розігрів із заміру, бюджет стосується
        // усталеного запиту, а не найпершого виклику в JVM.
        searchService.search(OrganizationId.of(ORGANIZATION_ID), query);

        long start = System.nanoTime();
        SearchResponse response = searchService.search(OrganizationId.of(ORGANIZATION_ID), query);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        log.info("[{}] query='{}' -> {} ms", label, query, elapsedMs);
        assertThat(elapsedMs)
                .as("SEN-22 AC: %s мало вкластись у %d мс на %d клієнтів, зайняло %d мс", label, RESPONSE_BUDGET_MS, CLIENT_COUNT, elapsedMs)
                .isLessThanOrEqualTo(RESPONSE_BUDGET_MS);
        return response;
    }

    @Test
    void fuzzyLastNameSearch_at10kClients_respondsWithinBudget() {
        SearchResponse response = timedSearch("прізвище (нечітко)", "Штепен");

        assertThat(response.clients()).extracting(c -> c.lastName()).containsExactly(TARGET_LAST_NAME);
    }

    @Test
    void exactRnokppSearch_at10kClients_respondsWithinBudget() {
        SearchResponse response = timedSearch("РНОКПП (точно)", TARGET_RNOKPP);

        assertThat(response.clients()).extracting(c -> c.rnokpp()).containsExactly(TARGET_RNOKPP);
    }

    @Test
    void exactEdrpouSearch_at10kClients_respondsWithinBudget() {
        SearchResponse response = timedSearch("ЄДРПОУ (точно)", TARGET_EDRPOU);

        assertThat(response.clients()).extracting(c -> c.edrpou()).containsExactly(TARGET_EDRPOU);
    }
}
