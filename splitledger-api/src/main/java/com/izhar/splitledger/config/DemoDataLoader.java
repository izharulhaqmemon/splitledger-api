package com.izhar.splitledger.config;

import com.izhar.splitledger.domain.SplitType;
import com.izhar.splitledger.dto.Dtos.*;
import com.izhar.splitledger.repository.GroupRepository;
import com.izhar.splitledger.service.LedgerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** On first start, creates a sample trip so that opening the GET endpoints in a browser shows real data. */
@Component
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
public class DemoDataLoader implements CommandLineRunner {

    private final GroupRepository groups;
    private final LedgerService service;

    public DemoDataLoader(GroupRepository groups, LedgerService service) {
        this.groups = groups;
        this.service = service;
    }

    @Override
    public void run(String... args) {
        if (groups.count() > 0) {
            return;
        }
        GroupResponse g = service.createGroup(new CreateGroupRequest("Murree Trip (demo)",
                List.of("Ali", "Sara", "Hamza", "Ayesha")));
        Long ali = g.members().get(0).id(), sara = g.members().get(1).id(),
                hamza = g.members().get(2).id(), ayesha = g.members().get(3).id();

        service.addExpense(g.id(), new CreateExpenseRequest("Hotel (3 nights)", new BigDecimal("24000.00"), ali,
                SplitType.EQUAL, List.of(split(ali, null), split(sara, null), split(hamza, null), split(ayesha, null))), "demo-1");

        service.addExpense(g.id(), new CreateExpenseRequest("Dinner at the hill", new BigDecimal("6400.00"), sara,
                SplitType.PERCENTAGE, List.of(split(ali, "40"), split(sara, "30"), split(hamza, "30"))), "demo-2");

        service.addExpense(g.id(), new CreateExpenseRequest("Fuel", new BigDecimal("5000.00"), hamza,
                SplitType.SHARES, List.of(split(ali, "2"), split(hamza, "2"), split(sara, "1"), split(ayesha, "1"))), "demo-3");

        service.addExpense(g.id(), new CreateExpenseRequest("Groceries", new BigDecimal("1800.50"), ayesha,
                SplitType.EXACT, List.of(split(ali, "600.50"), split(sara, "600.00"), split(ayesha, "600.00"))), "demo-4");
    }

    private static SplitInput split(Long memberId, String value) {
        return new SplitInput(memberId, value == null ? null : new BigDecimal(value));
    }
}
