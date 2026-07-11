import { getDemoGoodsImage, getDemoPetImage } from '@/utils/demoContentAssets'

describe('demo content assets', () => {
  it.each([
    ['豆包', 'pet-doubao.webp'],
    ['橘子', 'pet-juzi.webp'],
    ['可乐', 'pet-kele.webp'],
    ['奶糖', 'pet-naitang.webp']
  ])('maps seeded pet %s to a distinct image', (name, file) => {
    expect(getDemoPetImage({ name })).toBe(`/demo/cards/${file}`)
  })

  it.each([
    ['DEMO-DOG-FOOD-01', 'goods-dog-food.webp'],
    ['DEMO-CAT-FOOD-01', 'goods-cat-food.webp'],
    ['DEMO-TREAT-01', 'goods-treat.webp'],
    ['DEMO-BED-01', 'goods-bed.webp'],
    ['DEMO-LEASH-01', 'goods-leash.webp'],
    ['DEMO-TOY-01', 'goods-toy.webp'],
    ['DEMO-CARE-01', 'goods-dental.webp'],
    ['DEMO-CARE-02', 'goods-comb.webp']
  ])('maps seeded goods %s to a distinct image', (sku, file) => {
    expect(getDemoGoodsImage({ sku })).toBe(`/demo/cards/${file}`)
  })

  it('does not override ordinary user content', () => {
    expect(getDemoPetImage({ name: '自建宠物' })).toBe('')
    expect(getDemoGoodsImage({ sku: 'CUSTOM-01' })).toBe('')
  })
})
