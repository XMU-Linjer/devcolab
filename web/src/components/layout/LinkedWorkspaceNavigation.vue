<template>
  <nav class="linked-workspace-navigation" aria-label="工程上下文导航">
    <button class="nav-item" type="button" title="工作台" @click="emit('open-workspace')">
      <House class="nav-icon" />
      <span v-if="!collapsed" class="sidebar-label">工作台</span>
    </button>
    <button
      class="nav-item"
      :class="{ 'is-active': activeItem === 'linked' }"
      type="button"
      title="联动对照"
      @click="emit('open-linked')"
    >
      <Connection class="nav-icon" />
      <span v-if="!collapsed" class="sidebar-label">联动对照</span>
      <span v-if="!collapsed" class="sidebar-nav-badge">{{ linkedCount }}</span>
    </button>
    <button
      class="nav-item"
      :class="{ 'is-active': activeItem === 'review' }"
      type="button"
      title="待我评审"
      @click="emit('open-review')"
    >
      <Check class="nav-icon" />
      <span v-if="!collapsed" class="sidebar-label">待我评审</span>
      <span v-if="!collapsed" class="sidebar-nav-badge">{{ reviewCount }}</span>
    </button>
    <button
      class="nav-item"
      :class="{ 'is-active': activeItem === 'drift' }"
      type="button"
      title="文档漂移"
      @click="emit('open-drift')"
    >
      <Warning class="nav-icon" />
      <span v-if="!collapsed" class="sidebar-label">文档漂移</span>
      <span v-if="!collapsed" class="sidebar-nav-badge">{{ driftCount }}</span>
    </button>
  </nav>
</template>

<script setup lang="ts">
import { Check, Connection, House, Warning } from '@element-plus/icons-vue';

withDefaults(defineProps<{
  collapsed?: boolean;
  activeItem?: 'linked' | 'review' | 'drift';
  linkedCount?: number;
  reviewCount?: number;
  driftCount?: number;
}>(), {
  collapsed: false,
  activeItem: 'linked',
  linkedCount: 0,
  reviewCount: 0,
  driftCount: 0,
});

const emit = defineEmits<{
  'open-workspace': [];
  'open-linked': [];
  'open-review': [];
  'open-drift': [];
}>();
</script>

<style scoped>
.linked-workspace-navigation {
  display: grid;
  flex: 0 0 auto;
  gap: 3px;
  padding: 12px 8px 10px;
}

.sidebar-nav-badge {
  display: grid;
  min-width: 20px;
  height: 20px;
  margin-left: auto;
  place-items: center;
  border-radius: 999px;
  background: #eef1f6;
  color: #667085;
  font-size: 11px;
  font-weight: 600;
}

.nav-item.is-active .sidebar-nav-badge {
  background: #fff;
  color: #315ee8;
}
</style>
