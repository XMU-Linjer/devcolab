import unittest
from pathlib import Path
import sys

SERVICE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_ROOT))

from app.domain import BlockType, DocumentBlock, DocumentReviewContext, DocumentType
from app.rules import review_document


class ReviewRuleTests(unittest.TestCase):
    def test_requirement_without_acceptance_criteria_creates_high_issue(self):
        context = DocumentReviewContext.from_blocks(
            document_id="doc-1",
            document_version_id="ver-1",
            title="登录需求",
            document_type=DocumentType.REQUIREMENT,
            version_no=1,
            blocks=[
                DocumentBlock(
                    id="block-1",
                    type=BlockType.PARAGRAPH,
                    text="用户可以通过账号密码登录系统，登录成功后进入工作区列表，失败时展示错误提示。",
                    sort_order=1,
                )
            ],
        )

        result = review_document(context)

        self.assertTrue(any(issue.rule_id == "REQ_MISSING_ACCEPTANCE_CRITERIA" for issue in result.suggestions))

    def test_requirement_with_acceptance_criteria_does_not_create_acceptance_issue(self):
        context = DocumentReviewContext.from_blocks(
            document_id="doc-1",
            document_version_id="ver-1",
            title="登录需求",
            document_type=DocumentType.REQUIREMENT,
            version_no=1,
            blocks=[
                DocumentBlock(
                    id="block-1",
                    type=BlockType.PARAGRAPH,
                    text="验收标准：输入正确账号密码后进入工作区；输入错误密码时返回错误提示；刷新页面后会话可恢复。",
                    sort_order=1,
                )
            ],
        )

        result = review_document(context)

        self.assertFalse(any(issue.rule_id == "REQ_MISSING_ACCEPTANCE_CRITERIA" for issue in result.suggestions))

    def test_api_document_with_endpoint_but_without_fields_creates_contract_issue(self):
        context = DocumentReviewContext.from_blocks(
            document_id="doc-2",
            document_version_id="ver-2",
            title="创建订单 API",
            document_type=DocumentType.API,
            version_no=1,
            blocks=[
                DocumentBlock(
                    id="block-1",
                    type=BlockType.PARAGRAPH,
                    text="POST /api/orders 创建订单，客户端调用后生成订单。",
                    sort_order=1,
                )
            ],
        )

        result = review_document(context)

        self.assertTrue(any(issue.rule_id == "API_MISSING_FIELD_CONTRACT" for issue in result.suggestions))

    def test_meaningful_document_can_pass_without_issue(self):
        context = DocumentReviewContext.from_blocks(
            document_id="doc-3",
            document_version_id="ver-3",
            title="文档树需求",
            document_type=DocumentType.REQUIREMENT,
            version_no=1,
            blocks=[
                DocumentBlock(
                    id="block-1",
                    type=BlockType.PARAGRAPH,
                    text=(
                        "目标：用户可以在工作区内创建父子文档，并通过文档树快速定位。"
                        "主要流程：用户进入空间详情页，选择新建文档，填写标题和类型，系统在当前节点下创建子文档。"
                        "验收标准：创建子文档后文档树立即刷新；移动文档后层级正确；删除文档后不可再访问。"
                    ),
                    sort_order=1,
                )
            ],
        )

        result = review_document(context)

        self.assertEqual(0, result.issue_count)


if __name__ == "__main__":
    unittest.main()
