package com.leadrush.company.mapper;

import com.leadrush.company.dto.CompanyResponse;
import com.leadrush.company.dto.CreateCompanyRequest;
import com.leadrush.company.entity.Company;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CompanyMapper {

    @Mapping(target = "contactCount", ignore = true)  // set manually in service
    CompanyResponse toResponse(Company company);

    List<CompanyResponse> toResponseList(List<Company> companies);

    /**
     * Map request → entity. Uses setters (not builder) because parent class fields
     * (id, workspaceId, etc.) aren't in Lombok's @Builder.
     *
     * builder = @Builder.disableBuilder: tells MapStruct to use setters instead.
     */
    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    Company toEntity(CreateCompanyRequest request);

    /** Update existing entity with non-null fields from request. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
                 builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    void updateEntity(CreateCompanyRequest request, @MappingTarget Company company);
}
