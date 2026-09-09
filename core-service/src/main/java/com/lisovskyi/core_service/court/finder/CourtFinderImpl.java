package com.lisovskyi.core_service.court.finder;

import com.lisovskyi.core_service.court.Court;
import com.lisovskyi.core_service.court.CourtRepository;
import com.sentio.shared.entity.finder.AbstractEntityFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourtFinderImpl extends AbstractEntityFinder<Court, Long> implements CourtFinder {

    private final CourtRepository courtRepository;

    @Override
    protected JpaRepository<Court, Long> getRepository() {
        return courtRepository;
    }

    @Override
    protected String getEntityName() {
        return "Court";
    }
}
