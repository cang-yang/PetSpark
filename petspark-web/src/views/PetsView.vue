<template>
  <main class="pets-page">
    <h1>宠物目录</h1>
    <form class="filters" @submit.prevent="load">
      <input v-model="query.keyword" placeholder="搜索宠物名称" />
      <select v-model="query.species">
        <option value="">全部物种</option>
        <option value="DOG">狗</option>
        <option value="CAT">猫</option>
      </select>
      <button type="submit">查询</button>
    </form>
    <p v-if="loading">加载中...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="pets.length === 0">暂无宠物</p>
    <section v-else class="pet-grid">
      <article v-for="pet in pets" :key="pet.id" class="pet-card">
        <h2>{{ pet.name }}</h2>
        <p>{{ pet.species }} / {{ pet.breedName || '未知品种' }}</p>
        <p>领养状态：{{ pet.adoptionStatus }}</p>
      </article>
    </section>
  </main>
</template>

<script>
import { listPets } from '@/api/pets'

export default {
  name: 'PetsView',
  data() {
    return {
      query: { keyword: '', species: '', page: 1, size: 20 },
      pets: [],
      loading: false,
      error: ''
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const response = await listPets(this.query)
        this.pets = response.data.data.items || []
      } catch (error) {
        this.error = error.response?.data?.message || '宠物列表加载失败'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
