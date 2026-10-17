package com.turnero.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import static org.assertj.core.api.Assertions.*;

class StaffMemberSortTest {
    @Test
    void resolvesAllowedOrderingWithAscendingIdTieBreaker() {
        assertThat(StaffMemberSort.fromParameter(null)).isEqualTo(Sort.by("name", "id"));
        assertThat(StaffMemberSort.fromParameter("name,desc")).isEqualTo(
                Sort.by(Sort.Order.desc("name"), Sort.Order.asc("id")));
        for (String invalid : new String[]{"", "name", "id,asc", "name,ASC", "name,asc,id", "name,invalid"}) {
            assertThatThrownBy(() -> StaffMemberSort.fromParameter(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
