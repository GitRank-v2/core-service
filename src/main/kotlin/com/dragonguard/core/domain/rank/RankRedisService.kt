package com.dragonguard.core.domain.rank

import com.dragonguard.core.domain.contribution.dto.ContributionRequest
import com.dragonguard.core.domain.member.Member
import com.dragonguard.core.domain.organization.OrganizationType
import com.dragonguard.core.domain.rank.dto.MemberRank
import com.dragonguard.core.domain.rank.dto.ProfileRank
import com.dragonguard.core.domain.rank.exception.RankAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class RankRedisService(
    private val redisTemplate: RedisTemplate<String, String>,
) : RankService {
    override fun addContribution(
        contributionRequest: ContributionRequest,
        totalAmount: Int,
    ) {
        try {
            updateRank(MEMBER_RANK_KEY, contributionRequest.githubId, totalAmount)
            contributionRequest.organizationId?.let {
                updateRank("${ORGANIZATION_MEMBER_RANK_KEY}${it}", contributionRequest.githubId, totalAmount)
                updateRank(
                    "${ORGANIZATION_TYPE_RANK_KEY}${contributionRequest.organizationType?.name}",
                    contributionRequest.githubId,
                    totalAmount
                )
            }
        } catch (e: Exception) {
            throw RankAccessException.update(e)
        }
    }

    private fun updateRank(
        target: String,
        zSetMember: String,
        addPoint: Int,
    ) {
        redisTemplate.execute { connection ->
            connection.zSetCommands().zIncrBy(
                target.toByteArray(),
                addPoint.toDouble(),
                zSetMember.toByteArray(),
            )
        }
    }

    override fun getMemberRank(
        start: Long,
        end: Long,
    ): List<MemberRank> = getRank(MEMBER_RANK_KEY, start, end)

    override fun getOrganizationRank(
        organizationType: OrganizationType,
        start: Long,
        end: Long,
    ): List<MemberRank> = getRank("${ORGANIZATION_TYPE_RANK_KEY}${organizationType.name}", start, end)

    override fun getOrganizationMemberRank(
        organizationId: Long,
        start: Long,
        end: Long,
    ): List<MemberRank> = getRank("${ORGANIZATION_MEMBER_RANK_KEY}$organizationId", start, end)

    private fun getRank(
        target: String,
        start: Long,
        end: Long,
    ) = try {
        val rank =
            redisTemplate.execute { connection ->
                connection.zSetCommands().zRevRangeWithScores(
                    target.toByteArray(),
                    start,
                    end,
                )
            }

        rank?.map {
            MemberRank(it.value.toString(), it.score.toLong())
        } ?: emptyList()
    } catch (e: Exception) {
        throw RankAccessException.get(e)
    }

    override fun getMemberProfileRank(member: Member): ProfileRank =
        try {
            val totalMemberNum =
                getTotalMemberNum(member)
            val rank = getRankByMember(member) ?: 0L
            val organizationRank = getOrganizationRankByMember(member) ?: 0L

            if (totalMemberNum <= 3L) {
                val githubIds = findAllOrganizationMembers(member, totalMemberNum)
                ProfileRank(githubIds, rank.toInt(), organizationRank.toInt(), totalMemberNum == organizationRank + 1L)
            } else {
                val adjacentRanks = calculateAdjacentRanks(organizationRank, totalMemberNum)

                redisTemplate
                    .execute { connection ->
                        connection
                            .zSetCommands()
                            .zRange(
                                "${ORGANIZATION_MEMBER_RANK_KEY}${member.githubId}".toByteArray(),
                                adjacentRanks[0],
                                adjacentRanks[1],
                            )
                    }.let {
                        it?.map { memberByte -> memberByte.toString() }?.let { githubIds ->
                            ProfileRank(
                                githubIds,
                                rank.toInt(),
                                organizationRank.toInt(),
                                totalMemberNum == organizationRank + 1L,
                            )
                        }
                    } ?: ProfileRank.empty()
            }
        } catch (e: Exception) {
            throw RankAccessException.get(e)
        }

    override fun getMemberRank(member: Member): Int =
        getRankByMember(member)?.toInt() ?: 0

    private fun calculateAdjacentRanks(
        it: Long,
        totalMemberNum: Long,
    ): List<Long> =
        when (it) {
            0L -> listOf(0L, 2L)
            totalMemberNum - 1L -> listOf(totalMemberNum - 3L, totalMemberNum - 1L)
            else -> listOf(it - 1L, it + 1L)
        }

    private fun getRankByMember(member: Member): Long? =
        redisTemplate.execute { connection ->
            connection.zSetCommands().zRank(
                MEMBER_RANK_KEY.toByteArray(),
                member.githubId.toByteArray(),
            )
        }?.plus(1L)

    private fun getOrganizationRankByMember(member: Member): Long? =
        member.organization?.let {
            redisTemplate.execute { connection ->
                connection.zSetCommands().zRank(
                    "${ORGANIZATION_MEMBER_RANK_KEY}${it.id}".toByteArray(),
                    member.githubId.toByteArray(),
                )
            }
        }?.plus(1L)

    private fun findAllOrganizationMembers(
        member: Member,
        totalMemberNum: Long,
    ): List<String> =
        member.organization?.let {
            redisTemplate
                .execute { connection ->
                    connection.zSetCommands().zRangeWithScores(
                        "${ORGANIZATION_MEMBER_RANK_KEY}${it.id}".toByteArray(),
                        0,
                        totalMemberNum - 1,
                    )
                }?.map {
                    it.value.toString()
                }
        } ?: emptyList()

    private fun getTotalMemberNum(member: Member): Long =
        member.organization?.let {
            redisTemplate.execute { connection ->
                connection.zSetCommands().zCard(
                    "${ORGANIZATION_MEMBER_RANK_KEY}${it.id}".toByteArray(),
                )
            }
        } ?: 0L

    companion object {
        private const val MEMBER_RANK_KEY = "rank:member"
        private const val ORGANIZATION_TYPE_RANK_KEY = "rank:organization:type:"
        private const val ORGANIZATION_MEMBER_RANK_KEY = "rank:organization:member:"
    }
}
