<template>
  <article
    class="paragraph-block tiptap-block"
    :class="[
      `is-${block.type.toLowerCase()}`,
      { 'is-dirty': dirty, 'has-remote-update': remotePending },
    ]"
  >
    <div class="block-toolbar">
      <div class="block-identity">
        <span class="block-index">#{{ block.sortOrder + 1 }}</span>
        <span class="block-version">版本 {{ baseVersion }}</span>
        <el-tag size="small" effect="plain">{{ blockTypeText }}</el-tag>
        <el-tag size="small" type="info" effect="plain">Tiptap</el-tag>
      </div>

      <div v-if="editingUsers.length > 0" class="block-editing-users">
        <span
          v-for="user in editingUsers"
          :key="`${user.userId}-${user.startedAt}`"
          class="block-editing-pill"
        >
          <span class="block-editing-avatar">
            {{ user.username.slice(0, 1).toUpperCase() }}
          </span>
          {{ user.username }} 正在编辑
        </span>
      </div>

      <div class="block-actions">
        <el-tooltip content="上移">
          <el-button
            :icon="ArrowUp"
            circle
            size="small"
            :disabled="isFirst || busy || readonly"
            @click="emit('move-up', block)"
          />
        </el-tooltip>
        <el-tooltip content="下移">
          <el-button
            :icon="ArrowDown"
            circle
            size="small"
            :disabled="isLast || busy || readonly"
            @click="emit('move-down', block)"
          />
        </el-tooltip>
        <el-tooltip content="删除">
          <el-button
            :icon="Delete"
            circle
            size="small"
            type="danger"
            :disabled="busy || readonly"
            @click="emit('delete', block)"
          />
        </el-tooltip>
      </div>
    </div>

    <div class="tiptap-editor-shell" :class="{ 'is-disabled': busy || readonly }">
      <editor-content :editor="editor" />
    </div>

    <div class="block-footer">
      <span :class="statusClass">{{ statusText }}</span>
      <button
        class="block-save-button"
        type="button"
        :disabled="!dirty || readonly || remotePending"
        @mousedown.prevent="save"
      >
        {{ busy ? '保存中…' : '保存' }}
      </button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { ArrowDown, ArrowUp, Delete } from '@element-plus/icons-vue';
import StarterKit from '@tiptap/starter-kit';
import { EditorContent, useEditor } from '@tiptap/vue-3';
import { computed, ref, watch } from 'vue';

import type {
  DocumentBlock,
  DocumentBlockContent,
  DocumentBlockType,
  TiptapNode,
} from '@/api/block';
import type { EditingState } from '@/composables/useDocumentCollaboration';

const props = withDefaults(defineProps<{
  block: DocumentBlock;
  isFirst: boolean;
  isLast: boolean;
  busy?: boolean;
  readonly?: boolean;
  editingUsers?: EditingState[];
}>(), {
  editingUsers: () => [],
});

const emit = defineEmits<{
  save: [block: DocumentBlock, content: DocumentBlockContent];
  delete: [block: DocumentBlock];
  'move-up': [block: DocumentBlock];
  'move-down': [block: DocumentBlock];
  'editing-start': [block: DocumentBlock];
  'editing-stop': [block: DocumentBlock];
}>();

const persistedText = ref(props.block.content.text);
const draftText = ref(props.block.content.text);
const persistedDocument = ref<TiptapNode>(structuredDocument(props.block));
const draftDocument = ref<TiptapNode>(structuredDocument(props.block));
const baseVersion = ref(props.block.version);
const remotePending = ref(false);

const editor = useEditor({
  content: structuredDocument(props.block),
  editable: !props.busy && !props.readonly,
  extensions: [
    StarterKit.configure({
      blockquote: false,
      bold: false,
      bulletList: false,
      code: false,
      codeBlock: false,
      dropcursor: false,
      gapcursor: false,
      heading: false,
      horizontalRule: false,
      italic: false,
      link: false,
      listItem: false,
      listKeymap: false,
      orderedList: false,
      strike: false,
      trailingNode: false,
      underline: false,
    }),
  ],
  editorProps: {
    attributes: {
      class: 'tiptap-content',
      role: 'textbox',
      'aria-multiline': 'true',
      'aria-label': '文档内容块编辑器',
    },
    transformPastedHTML: (html) => plainTextHtml(html),
  },
  onUpdate: ({ editor: current }) => {
    draftText.value = plainText(current);
    draftDocument.value = current.getJSON() as TiptapNode;
  },
  onFocus: () => {
    if (!props.busy && !props.readonly) {
      emit('editing-start', props.block);
    }
  },
  onBlur: () => {
    save();
    if (!props.readonly) {
      emit('editing-stop', props.block);
    }
  },
});

const dirty = computed(() => (
  JSON.stringify(draftDocument.value) !== JSON.stringify(persistedDocument.value)
));
const blockTypeText = computed(() => blockTypeLabels[props.block.type]);
const statusText = computed(() => {
  if (props.readonly) {
    return '只读';
  }
  if (remotePending.value) {
    return '检测到远端更新，请刷新后继续编辑';
  }
  if (props.busy) {
    return '保存中…';
  }
  return dirty.value ? '编辑中，离开编辑区后保存' : '已保存';
});
const statusClass = computed(() => (
  dirty.value || remotePending.value ? 'text-warning' : 'text-muted'
));

watch(
  () => [props.busy, props.readonly] as const,
  ([busy, readonly]) => {
    editor.value?.setEditable(!busy && !readonly);
  },
);

watch(
  () => props.block,
  (block) => {
    const currentDocument = editor.value
      ? editor.value.getJSON()
      : draftDocument.value;
    if (block.version === baseVersion.value
      && JSON.stringify(structuredDocument(block)) === JSON.stringify(persistedDocument.value)) {
      return;
    }
    if (dirty.value
      && JSON.stringify(structuredDocument(block)) !== JSON.stringify(currentDocument)) {
      remotePending.value = true;
      return;
    }
    applyServerBlock(block);
  },
);

function save() {
  if (!dirty.value || props.busy || props.readonly || remotePending.value) {
    return;
  }
  emit('save', {
    ...props.block,
    version: baseVersion.value,
    content: {
      text: persistedText.value,
      schemaVersion: props.block.content.schemaVersion,
      document: persistedDocument.value,
    },
  }, {
    text: draftText.value,
    schemaVersion: 1,
    document: draftDocument.value,
  });
}

function applyServerBlock(block: DocumentBlock) {
  persistedText.value = block.content.text;
  draftText.value = block.content.text;
  persistedDocument.value = structuredDocument(block);
  draftDocument.value = structuredDocument(block);
  baseVersion.value = block.version;
  remotePending.value = false;
  editor.value?.commands.setContent(structuredDocument(block), {
    emitUpdate: false,
  });
}

function plainText(current: {
  getText: (options?: { blockSeparator?: string }) => string;
}) {
  return current.getText({ blockSeparator: '\n' });
}

function textDocument(text: string) {
  return {
    type: 'doc',
    content: text.split('\n').map((line) => ({
      type: 'paragraph',
      content: line.length > 0 ? [{ type: 'text', text: line }] : [],
    })),
  };
}

function structuredDocument(block: DocumentBlock): TiptapNode {
  return block.content.document ?? textDocument(block.content.text);
}

function plainTextHtml(html: string) {
  const document = new DOMParser().parseFromString(html, 'text/html');
  const escaped = (document.body.textContent ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
  return `<p>${escaped.replace(/\r?\n/g, '</p><p>')}</p>`;
}

const blockTypeLabels: Record<DocumentBlockType, string> = {
  PARAGRAPH: '段落',
  HEADING: '标题',
  CODE: '代码',
  TODO: '待办',
};
</script>
