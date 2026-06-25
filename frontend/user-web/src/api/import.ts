import api from './index'

export interface ImportSummary {
  shotsCreated: number
  charactersCreated: number
  scenesCreated: number
  propsCreated: number
  bindingsCreated: number
  imagesMatched: number
  imagesUploaded: number
}

/**
 * 分镜表格 Excel 导入 API
 *
 * 支持 MochiAni 风格 .xlsx：Sheet 包含「分镜 / 出场人物 / 场景 / 道具」
 */
export const importApi = {
  /**
   * 上传 .xlsx 到指定项目，追加分镜并按名称合并角色/场景/道具
   * 解析大文件可能较慢，超时设为 5 分钟
   */
  async importStoryboardExcel(projectId: number, file: File, assetFiles: File[] = []): Promise<ImportSummary> {
    const form = new FormData()
    form.append('file', file)
    assetFiles.forEach((assetFile) => {
      const uploadName = (assetFile as any).webkitRelativePath || assetFile.name
      form.append('assetFiles', assetFile, uploadName)
    })
    return api.post(`/projects/${projectId}/import/excel`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
    })
  },

  /**
   * 为已导入项目补充上传角色/场景/道具图片
   */
  async importAssetImages(projectId: number, assetFiles: File[]): Promise<ImportSummary> {
    const form = new FormData()
    assetFiles.forEach((assetFile) => {
      const uploadName = (assetFile as any).webkitRelativePath || assetFile.name
      form.append('assetFiles', assetFile, uploadName)
    })
    return api.post(`/projects/${projectId}/import/asset-images`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
    })
  },
}
