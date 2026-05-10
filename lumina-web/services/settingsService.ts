import { api } from '../utils/request';

interface SelfUseModeResponse {
  code: number;
  message?: string;
  data?: { enabled: boolean };
}

export const settingsService = {
  /**
   * 获取自用模式当前状态。
   * 失败（网络/后端异常）时默认返回 false，避免 UI 被异常阻塞。
   */
  async getSelfUseMode(): Promise<boolean> {
    try {
      const res = await api.get<SelfUseModeResponse>('/settings/self-use-mode');
      if (res.code === 200 && res.data) {
        return Boolean(res.data.enabled);
      }
      return false;
    } catch (err) {
      console.error('Failed to fetch self-use mode', err);
      return false;
    }
  },

  /**
   * 更新自用模式开关，成功时返回后端确认的状态。
   */
  async setSelfUseMode(enabled: boolean): Promise<boolean> {
    const res = await api.put<SelfUseModeResponse>('/settings/self-use-mode', { enabled });
    if (res.code !== 200 || !res.data) {
      throw new Error(res.message || 'Failed to update self-use mode');
    }
    return Boolean(res.data.enabled);
  },
};
