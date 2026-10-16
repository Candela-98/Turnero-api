package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.ServOfferingUpdateRequestDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.exception.InvalidStateTransitionException;
import org.springframework.transaction.annotation.Transactional;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.repository.ServOfferingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.time.LocalDateTime;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ServOfferingServiceImpl implements ServOfferingService {
    private final ServOfferingRepository servOfferingRepository;

    private final CurrentBusinessContext currentBusinessContext;

    @Override
    public ServiceOffering saveServiceOffering(ServiceOffering serviceOffering) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        serviceOffering.setBusinessId(businessId);
        LocalDateTime now = LocalDateTime.now();
        serviceOffering.setCreatedAt(now);
        serviceOffering.setUpdatedAt(now);

        if (serviceOffering.getStatus() == null) {
            serviceOffering.setStatus(ServiceOfferingStatus.ACTIVE);
        }

        ServiceOffering savedServiceOffering = servOfferingRepository.save(serviceOffering);
        log.info("Service offering created with id={} for businessId={}", savedServiceOffering.getId(), businessId);
        return savedServiceOffering;
    }

    @Override
    public ServiceOffering findServiceOffering(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        return servOfferingRepository.findByIdAndBusinessId(id, businessId)
                .filter(service -> service.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Service offering not found with ID: " + id));
    }

    @Override
    public Page<ServiceOffering> listServiceOfferings(String q, String category, ServiceOfferingStatus status, int page, int size, Sort ordering) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        if (page < 0 || size < 1 || size > 100 || (q != null && q.length() > 100)
                || (category != null && category.length() > 255)) {
            throw new IllegalArgumentException("Invalid service offering list parameter");
        }
        String search = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        String selectedCategory = category == null ? null : category.trim();
        Specification<ServiceOffering> filters = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("businessId"), businessId));
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (search != null && !search.isEmpty()) {
                String pattern = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("category")), pattern, '\\')));
            }
            if (selectedCategory != null && !selectedCategory.isEmpty()) predicates.add(builder.equal(root.get("category"), selectedCategory));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return servOfferingRepository.findAll(filters, PageRequest.of(page, size, ordering));
    }

    @Override
    public List<String> listCategories() {
        return servOfferingRepository.findCategoriesByBusinessId(currentBusinessContext.getCurrentBusinessId());
    }

    @Override
    @Transactional
    public ServiceOffering updateServOffering(ServOfferingUpdateRequestDto servOfferigUpdateDto, Long id) {
        ServiceOffering currentServOffering = findForUpdate(id);

        if (servOfferigUpdateDto.getName() != null) {
            currentServOffering.setName(servOfferigUpdateDto.getName());
        }

        if (servOfferigUpdateDto.getCategory() != null) {
            currentServOffering.setCategory(servOfferigUpdateDto.getCategory());
        }

        if (servOfferigUpdateDto.getDurationMinutes() != null) {
            currentServOffering.setDurationMinutes(servOfferigUpdateDto.getDurationMinutes());
        }

        if (servOfferigUpdateDto.getPriceCents() != null) {
            currentServOffering.setPriceCents(servOfferigUpdateDto.getPriceCents());
        }

        if (servOfferigUpdateDto.getStatus() != null) {
            currentServOffering.setStatus(servOfferigUpdateDto.getStatus());
        }

        currentServOffering.setUpdatedAt(LocalDateTime.now());
        ServiceOffering updatedServiceOffering = servOfferingRepository.save(currentServOffering);

        log.info("Service offering with id={} successfully updated.", id);

        return updatedServiceOffering;

    }

    @Override
    @Transactional
    public void deleteServOffering(Long id) {
        ServiceOffering serviceOffering = findForUpdate(id);

        serviceOffering.setStatus(ServiceOfferingStatus.INACTIVE);
        serviceOffering.setUpdatedAt(LocalDateTime.now());

        servOfferingRepository.save(serviceOffering);

        log.info("Service offering with id={} successfully deactivated for businessId={}.", id, serviceOffering.getBusinessId());
    }
    @Override
    @Transactional
    public void removeFromCatalog(Long id) {
        ServiceOffering service = findForUpdate(id);
        if (service.getStatus() != ServiceOfferingStatus.INACTIVE) {
            throw new InvalidStateTransitionException("Deactivate the service before removing it from the catalog");
        }
        LocalDateTime now = LocalDateTime.now();
        service.setDeletedAt(now);
        service.setUpdatedAt(now);
        servOfferingRepository.save(service);
    }

    private ServiceOffering findForUpdate(Long id) {
        return servOfferingRepository.findForUpdate(id, currentBusinessContext.getCurrentBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Service offering not found with ID: " + id));
    }

}
