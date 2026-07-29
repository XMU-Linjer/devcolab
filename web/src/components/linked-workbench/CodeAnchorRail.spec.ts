import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import CodeAnchorRail from './CodeAnchorRail.vue';

describe('CodeAnchorRail', () => {
  it('renders no numbered node when there is no valid formal binding', () => {
    const wrapper = mount(CodeAnchorRail, {
      props: {
        links: [],
        anchors: [],
        issues: [],
        activeLinkId: null,
      },
    });

    expect(wrapper.findAll('.rail-node')).toHaveLength(0);
    expect(wrapper.get('.rail-empty').text()).toContain('暂无');
  });
});
