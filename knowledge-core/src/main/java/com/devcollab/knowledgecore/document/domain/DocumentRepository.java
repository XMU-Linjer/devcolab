package com.devcollab.knowledgecore.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

//Document 聚合根的仓储接口（领域层）
public interface DocumentRepository {

    /**
     * 保存文档（新增或更新）。
     * <p>
     * 若文档 ID 已存在则更新，否则新增。
     * 返回持久化后的文档实例（可能包含数据库生成的默认值）。
     * </p>
     * @param document 要保存的文档
     * @return 持久化后的文档
     */
    Document save(Document document);

    /**
     * 按 ID 只读查询文档。
     * @param documentId 文档 ID
     * @return 文档，若不存在则返回 {@code Optional.empty()}
     */
    Optional<Document> findById(UUID documentId);

    /**
     * 按 ID 查询文档，用于后续更新操作。
     * <p>
     * 默认实现等同于 {@link #findById}（依赖乐观锁）。
     * 在需要悲观锁的场景下，基础设施层实现可覆写此方法，
     * 通过 {@code SELECT ... FOR UPDATE} 对数据行加锁。
     * </p>
     * @param documentId 文档 ID
     * @return 文档，若不存在则返回 {@code Optional.empty()}
     */
    default Optional<Document> findByIdForUpdate(UUID documentId) {
        return findById(documentId);
    }

    /**
     * 按工作空间查询该空间下全部文档（不含软删除文档）。
     * @param workspaceId 工作空间 ID
     * @return 文档列表，无数据时返回空列表
     */
    List<Document> findAllByWorkspaceId(UUID workspaceId);

    /**
     * 按 ID 删除文档。
     * <p>
     * 建议使用软删除（标记删除位而非物理删除），
     * 具体策略由基础设施层实现决定。
     * </p>
     *
     * @param documentId 文档 ID
     */
    void deleteById(UUID documentId);
}
