<template>
  <div class="permission-picker" data-testid="permission-group-picker">
    <section
      v-for="group in groups"
      :key="group.resource"
      class="permission-group"
      :aria-labelledby="`permission-group-${group.resource}`"
    >
      <header>
        <div><strong :id="`permission-group-${group.resource}`">{{ group.label }}</strong><code>{{ group.resource }}</code></div>
        <span>{{ selectedCount(group) }}/{{ group.permissions.length }} 项</span>
      </header>
      <el-checkbox-group :value="value" @input="$emit('input', $event)">
        <el-checkbox v-for="permission in group.permissions" :key="permission.code" :label="permission.code">
          <span class="permission-name">{{ permissionLabel(permission) }}</span>
          <code>{{ permission.code }}</code>
        </el-checkbox>
      </el-checkbox-group>
    </section>
  </div>
</template>

<script>
import { getPermissionLabel } from '@/utils/adminTerminology'

export default {
  name: 'PermissionGroupPicker',
  props: {
    value: { type: Array, default: () => [] },
    groups: { type: Array, default: () => [] }
  },
  methods: {
    permissionLabel(permission) { return getPermissionLabel(permission) },
    selectedCount(group) {
      const selected = new Set(this.value)
      return group.permissions.filter(permission => selected.has(permission.code)).length
    }
  }
}
</script>

<style scoped>
.permission-picker { display: grid; gap: 12px; max-height: 470px; overflow-y: auto; padding-right: 5px; }
.permission-group { padding: 14px; background: var(--ps-color-surface-soft); border: 1px solid var(--ps-color-border); border-radius: 12px; }
.permission-group header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.permission-group header div { display: flex; align-items: baseline; gap: 8px; }
.permission-group header strong { color: var(--ps-color-text); }
.permission-group header code, .permission-group header span { color: var(--ps-color-muted); font-size: 12px; }
.permission-group ::v-deep .el-checkbox-group { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.permission-group ::v-deep .el-checkbox { display: flex; align-items: flex-start; min-width: 0; margin: 0; padding: 9px; background: var(--ps-color-surface); border-radius: 8px; white-space: normal; }
.permission-group ::v-deep .el-checkbox__label { display: grid; min-width: 0; gap: 3px; }
.permission-name { color: var(--ps-color-text); font-size: 13px; }
.permission-group code { color: var(--ps-color-muted); font-size: 11px; overflow-wrap: anywhere; }
@media (max-width: 560px) { .permission-group ::v-deep .el-checkbox-group { grid-template-columns: 1fr; } }
</style>
