package com.petspark.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.petspark.audit.AuditService;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 复现 PR-FILE-01 验收要求中的"失败孤儿清理"：当元数据写入数据库失败时，
 * 已写入本地存储的文件必须被删除，不能留下指向不存在元数据的孤儿对象。
 *
 * <p>纯单元测试，不启动 Spring 上下文、不依赖本机 MySQL：用 Mockito 桩
 * {@link FileObjectRepository#insert} 抛出异常，断言 {@link FileStorage#delete}
 * 被调用且 objectKey 与 {@code store} 时一致，从而保证磁盘与元数据不出现悬挂。
 */
class FileServiceOrphanCleanupTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Test
    void repositoryInsertFailureDeletesStoredOrphan() {
        ImageValidator validator = new ImageValidator();
        FileStorage storage = mock(FileStorage.class);
        FileObjectRepository repository = mock(FileObjectRepository.class);
        AuditService auditService = mock(AuditService.class);

        doThrow(new RuntimeException("simulated insert failure"))
                .when(repository).insert(any(FileObject.class));

        FileService service = new FileService(validator, storage, repository, auditService);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, PNG);

        // insert 失败时原始异常应向上抛出，而不是被吞掉
        assertThatThrownBy(() -> service.uploadImage(file, "PROFILE_AVATAR", "owner-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated insert failure");

        // store 先写入磁盘
        ArgumentCaptor<String> storedKey = ArgumentCaptor.forClass(String.class);
        verify(storage, times(1)).store(storedKey.capture(), any(byte[].class));

        // insert 失败后必须删除孤儿，且使用与 store 相同的 objectKey
        ArgumentCaptor<String> deletedKey = ArgumentCaptor.forClass(String.class);
        verify(storage, times(1)).delete(deletedKey.capture());

        assertThat(deletedKey.getValue()).isEqualTo(storedKey.getValue());
    }
}
