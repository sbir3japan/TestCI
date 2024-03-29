package net.corda.crypto.impl.retrying

import net.corda.crypto.core.CryptoRetryException
import net.corda.v5.crypto.exceptions.CryptoException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeoutException
import javax.persistence.LockTimeoutException
import javax.persistence.OptimisticLockException
import javax.persistence.PersistenceException
import javax.persistence.PessimisticLockException
import javax.persistence.QueryTimeoutException

class CryptoRetryingExecutorsTests {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @JvmStatic
        fun mostCommonUnrecoverableExceptions(): List<Throwable> = listOf(
            IllegalStateException(),
            IllegalArgumentException(),
            NullPointerException(),
            IndexOutOfBoundsException(),
            NoSuchElementException(),
            RuntimeException(),
            ClassCastException(),
            NotImplementedError(),
            UnsupportedOperationException(),
            CryptoException("error"),
            CryptoException(
                "error",
                CryptoException("error", true)
            ),
            CryptoException(
                "error",
                TimeoutException()
            ),
            CryptoRetryException("error", TimeoutException()),
            PersistenceException()
        )

        @JvmStatic
        fun recoverableExceptions(): List<Throwable> = listOf(
            CryptoException("error", true),
            TimeoutException(),
            LockTimeoutException(),
            QueryTimeoutException(),
            OptimisticLockException(),
            PessimisticLockException(),
            java.sql.SQLTransientException(),
            java.sql.SQLTimeoutException(),
            org.hibernate.exception.LockAcquisitionException("error", java.sql.SQLException()),
            org.hibernate.exception.LockTimeoutException("error", java.sql.SQLException()),
            RuntimeException("error", TimeoutException()),
            PersistenceException("error", LockTimeoutException()),
            PersistenceException("error", QueryTimeoutException()),
            PersistenceException("error", OptimisticLockException()),
            PersistenceException("error", PessimisticLockException()),
            PersistenceException("error", java.sql.SQLTransientException()),
            PersistenceException("error", java.sql.SQLTimeoutException()),
            PersistenceException(
                "error", org.hibernate.exception.LockAcquisitionException(
                    "error", java.sql.SQLException()
                )
            ),
            PersistenceException(
                "error", org.hibernate.exception.LockTimeoutException(
                    "error", java.sql.SQLException()
                )
            )
        )
    }

    @Test
    fun `Should execute without retrying`() {
        var called = 0
        val result = CryptoRetryingExecutor(
            logger, 3, listOf(100L)
        ).executeWithRetry {
            called++
            "Hello World!"
        }

        assertThat(called).isEqualTo(1)
        assertThat(result).isEqualTo("Hello World!")
    }

    @Test
    fun `Should eventually fail with retrying`() {
        var called = 0
        val actual = assertThrows<CryptoRetryException> {
            CryptoRetryingExecutor(
                logger, 3, listOf(10L)
            ).executeWithRetry {
                called++
                throw TimeoutException()
            }
        }

        assertThat(called).isEqualTo(3)
        assertThat(actual.cause).isInstanceOf(TimeoutException::class.java)
    }

    @Test
    fun `Should eventually succeed after retrying TimeoutException`() {
        var called = 0
        val result = CryptoRetryingExecutor(
            logger, 3, listOf(10L)
        ).executeWithRetry {
            called++
            if (called <= 2) {
                throw TimeoutException()
            }
            "Hello World!"
        }

        assertThat(called).isEqualTo(3)
        assertThat(result).isEqualTo("Hello World!")
    }

    @ParameterizedTest
    @MethodSource("recoverableExceptions")
    fun `Should eventually succeed after retrying recoverable exception`(
        e: Throwable
    ) {
        var called = 0
        val result = CryptoRetryingExecutor(
            logger, 3, listOf(10L)
        ).executeWithRetry {
            called++
            if (called <= 2) {
                throw e
            }
            "Hello World!"
        }

        assertThat(called).isEqualTo(3)
        assertThat(result).isEqualTo("Hello World!")
    }

    @ParameterizedTest
    @MethodSource("recoverableExceptions")
    fun `Should retry all recoverable exceptions`(e: Throwable) {
        var called = 0
        val result = CryptoRetryingExecutor(
            logger, 2, listOf(10L)
        ).executeWithRetry {
            called++
            if (called < 2) {
                throw e
            }
            "Hello World!"
        }

        assertThat(called).isEqualTo(2)
        assertThat(result).isEqualTo("Hello World!")
    }
}
