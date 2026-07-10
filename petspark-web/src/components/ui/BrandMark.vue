<template>
  <span class="brand-mark" :class="{ 'brand-mark--inverse': inverse }">
    <img
      class="brand-mark__symbol"
      :src="brandSymbol"
      alt=""
      width="42"
      height="42"
      data-testid="brand-symbol"
    >
    <span v-if="!compact" class="brand-mark__copy">
      <strong>PetSpark</strong>
      <small v-if="tagline">{{ tagline }}</small>
    </span>
  </span>
</template>

<script>
const brandSymbol = `${process.env.BASE_URL || '/'}petspark-mark.svg`

export default {
  name: 'BrandMark',
  props: {
    tagline: { type: String, default: '' },
    compact: { type: Boolean, default: false },
    inverse: { type: Boolean, default: false }
  },
  data() {
    return { brandSymbol }
  }
}
</script>

<style scoped>
.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}
.brand-mark__symbol {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  border-radius: 13px;
}
.brand-mark__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.08;
}
.brand-mark__copy strong {
  color: var(--ps-color-text);
  font-size: 17px;
  font-weight: 850;
  letter-spacing: -0.025em;
}
.brand-mark__copy small {
  margin-top: 4px;
  color: var(--ps-color-muted);
  font-size: 11px;
  font-weight: 550;
  letter-spacing: 0;
  white-space: nowrap;
}
.brand-mark--inverse .brand-mark__copy strong { color: #fff; }
.brand-mark--inverse .brand-mark__copy small { color: #b8c6d5; }
</style>
