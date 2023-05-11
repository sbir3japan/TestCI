package net.corda.processors.crypto.tests

import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock


class RotateTests {
    @Test
    fun `test`() {
        val mockVirtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
            on { getAll() }.doReturn(listOf(mock()))
        }
        val r = rotate(mock(), mock())
        assertThat(r.numberOfTenants).isEqualTo(1)
    }
}