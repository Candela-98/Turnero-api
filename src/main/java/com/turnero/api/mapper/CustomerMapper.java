package com.turnero.api.mapper;

import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.dto.CustomerResponseDto;
import com.turnero.api.model.Customer;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "name", target = "name")
    @Mapping(source = "phone", target = "phoneNumber")
    @Mapping(target = "businessId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "internalNotes", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Customer toEntity(CustomerRequestDto dtoCustomer);

    CustomerResponseDto toResponseDto(Customer customer);

    @Named("toSummaryResponseDto")
    @Mapping(target = "internalNotes", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CustomerResponseDto toSummaryResponseDto(Customer customer);

    @IterableMapping(qualifiedByName = "toSummaryResponseDto")
    List<CustomerResponseDto> toSummaryResponseDtoList(List<Customer> customers);
}
