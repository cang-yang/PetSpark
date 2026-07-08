<template>
  <main>
    <h1>我的宠物</h1>
    <form @submit.prevent="submit">
      <input v-model="form.name" placeholder="宠物名称" required />
      <select v-model="form.species">
        <option value="DOG">狗</option>
        <option value="CAT">猫</option>
      </select>
      <button type="submit">新增宠物</button>
    </form>
    <p v-if="message">{{ message }}</p>
  </main>
</template>

<script>
import { createMyPet } from '@/api/pets'

export default {
  name: 'MyPetsView',
  data() {
    return { form: { name: '', species: 'DOG' }, message: '' }
  },
  methods: {
    async submit() {
      await createMyPet(this.form)
      this.message = '宠物已保存'
    }
  }
}
</script>
