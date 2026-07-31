package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DocumentBlock 的仓储接口（领域层）
 * @see DocumentBlock
 * @see DocumentBlockRepository
 */
public interface DocumentBlockRepository {

    /**
     * 保存单个 Block（新增或更新，不检查版本）。
     * <p>
     * 适用于新建 Block 或强制覆盖场景。
     * 需要乐观锁保护的更新请使用 {@link #updateContentIfVersionMatches}。
     * </p>
     *
     * @param block 要保存的 Block
     * @return 持久化后的 Block
     */
    DocumentBlock save(DocumentBlock block);

    /**
     * 批量保存多个 Block。
     * <p>
     * 适用于一次性插入整个文档的所有 Block（如导入文档），
     * 实现层应尽量使用批量 SQL 以减少数据库往返。
     * </p>
     *
     * @param blocks 要保存的 Block 列表
     * @return 持久化后的 Block 列表
     */
    List<DocumentBlock> saveAll(List<DocumentBlock> blocks);

    /**
     * 按 ID 只读查询 Block。
     *
     * @param blockId Block ID
     * @return Block，若不存在则返回 {@code Optional.empty()}
     */
    Optional<DocumentBlock> findById(UUID blockId);

    /**
     * 按 ID 查询 Block，用于后续更新操作。
     * <p>
     * 默认实现等同于 {@link #findById}（依赖乐观锁）。
     * 在需要悲观锁的场景下，基础设施层实现可覆写此方法，
     * 通过 {@code SELECT ... FOR UPDATE} 对数据行加锁。
     * </p>
     *
     * @param blockId Block ID
     * @return Block，若不存在则返回 {@code Optional.empty()}
     */
    default Optional<DocumentBlock> findByIdForUpdate(UUID blockId) {
        return findById(blockId);
    }

    /**
     * 查询某个文档下的全部 Block，按 {@code sortOrder} 升序排列。
     *
     * @param documentId 所属文档 ID
     * @return Block 列表（已排序），无数据时返回空列表
     */
    List<DocumentBlock> findAllByDocumentId(UUID documentId);

    /**
     * 原子更新 Block 内容——仅当版本匹配时才写入（乐观锁）。
     *
     * <p>
     * 实现层应使用原子 SQL 保证检查-写入不可分割，
     * 典型写法：{@code UPDATE ... SET ... version = version + 1 WHERE id = ? AND version = ?}。
     * </p>
     *
     * @param blockId              Block ID
     * @param text                 新的纯文本内容
     * @param contentSchemaVersion 新的 JSON 格式版本号
     * @param contentJson          新的富文本 JSON
     * @param updatedAt            更新时间戳
     * @param expectedVersion      期望的当前版本号（即读出时的 version）
     * @return 更新后的 Block，若版本不匹配则返回 {@code Optional.empty()}
     */
    Optional<DocumentBlock> updateContentIfVersionMatches(
            UUID blockId,
            String text,
            int contentSchemaVersion,
            String contentJson,
            Instant updatedAt,
            long expectedVersion
    );

    /**
     * 原子删除 Block——仅当版本匹配时才执行（乐观锁）。
     *
     * <p>
     * 防止用户在 A 的界面中删除 Block，而 B 恰好同时修改了它，
     * 导致 B 的修改被静默丢弃。
     * </p>
     *
     * @param blockId         Block ID
     * @param expectedVersion 期望的当前版本号
     * @return {@code true} 删除成功；{@code false} 版本不匹配，删除被拒绝
     */
    boolean deleteIfVersionMatches(UUID blockId, long expectedVersion);

    /**
     * 按 ID 强制删除 Block（不检查版本）。
     * <p>
     * 适用于管理员操作、文档整体删除等无需冲突检测的场景。
     * 需要乐观锁保护的删除请使用 {@link #deleteIfVersionMatches}。
     * </p>
     *
     * @param blockId Block ID
     */
    void deleteById(UUID blockId);
}
