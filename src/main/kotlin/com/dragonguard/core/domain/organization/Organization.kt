package com.dragonguard.core.domain.organization

import com.dragonguard.core.domain.member.Member
import com.dragonguard.core.global.audit.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import org.hibernate.annotations.SoftDelete

@Entity
@SoftDelete
class Organization(
    @Column(nullable = false, unique = true)
    var name: String,
    var emailEndpoint: String,
    @Enumerated(EnumType.STRING)
    var organizationType: OrganizationType,
) : BaseEntity() {
    fun validateAndUpdateEmail(member: Member, email: String) {
        if (!email.endsWith(emailEndpoint)) {
            throw InvalidEmailException()
        }
        member.email = email
    }

    @Enumerated(EnumType.STRING)
    var organizationStatus: OrganizationStatus = OrganizationStatus.REQUESTED

    fun approve() {
        this.organizationStatus = OrganizationStatus.APPROVED
    }
}
