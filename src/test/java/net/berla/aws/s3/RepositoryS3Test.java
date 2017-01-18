package net.berla.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.StringUtils;
import net.berla.aws.Status;
import net.berla.aws.git.Branch;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;

import static net.berla.aws.Status.SUCCESS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("Duplicates")
@RunWith(MockitoJUnitRunner.class)
public class RepositoryS3Test {

    private static final Bucket BUCKET = new Bucket("TestBucket");

    private final AmazonS3 amazonS3 = mock(AmazonS3.class);

    private final TestRepository<InMemoryRepository> repository;

    private final RepositoryS3 uut;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    public RepositoryS3Test() {
        try {
            this.repository = new TestRepository<>(new InMemoryRepository(new DfsRepositoryDescription()));
            this.uut = new RepositoryS3(BUCKET, repository.getRepository(), amazonS3, new Branch(Constants.MASTER));
        }
        catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    public void shouldSync() throws Exception {
        // Given
        String pathReadmeMd = "README.md";
        String contentReadmeMd = "This is a test file";
        this.repository.branch(Constants.MASTER).commit().add(pathReadmeMd, this.repository.blob(contentReadmeMd)).create();

        ObjectListing result = mock(ObjectListing.class);
        when(result.isTruncated()).thenReturn(false);
        when(result.getObjectSummaries()).thenReturn(new ArrayList<S3ObjectSummary>(2) {{
            S3ObjectSummary summary;

            summary = new S3ObjectSummary();
            summary.setBucketName(BUCKET.getName());
            summary.setKey(".git/test");
            summary.setETag("123");
            add(summary);

            summary = new S3ObjectSummary();
            summary.setBucketName(BUCKET.getName());
            summary.setKey(pathReadmeMd);
            summary.setETag("123");
            add(summary);
        }});
        when(amazonS3.listObjects(BUCKET.getName())).thenReturn(result);
        when(amazonS3.putObject(eq(BUCKET.getName()), any(), any(), any())).thenReturn(null);
        doNothing().when(amazonS3).deleteObject(eq(BUCKET.getName()), any());

        // When
        Status status = uut.call();

        // Then
        assertThat(status, is(SUCCESS));
        verify(amazonS3, times(1)).listObjects(eq(BUCKET.getName()));
        verify(amazonS3, times(1)).putObject(eq(BUCKET.getName()), eq(pathReadmeMd), any(), any());
        verify(amazonS3, times(0)).deleteObject(eq(BUCKET.getName()), any());
        verifyNoMoreInteractions(amazonS3);
    }

    @Test
    public void shouldCompareHashes() throws Exception {
        // Given
        String pathReadmeMd = "README.md";
        String contentReadmeMd = "This is a test file";
        this.repository.branch(Constants.MASTER).commit().add(pathReadmeMd, this.repository.blob(contentReadmeMd)).create();

        ObjectListing result = mock(ObjectListing.class);
        when(result.isTruncated()).thenReturn(false);
        when(result.getObjectSummaries()).thenReturn(new ArrayList<S3ObjectSummary>(2) {{
            S3ObjectSummary summary;

            summary = new S3ObjectSummary();
            summary.setBucketName(BUCKET.getName());
            summary.setKey(".git/test");
            summary.setETag("123");
            add(summary);

            summary = new S3ObjectSummary();
            summary.setBucketName(BUCKET.getName());
            summary.setKey(pathReadmeMd);
            summary.setETag(DigestUtils.md5Hex(contentReadmeMd.getBytes(StringUtils.UTF8)));
            add(summary);
        }});
        when(amazonS3.listObjects(BUCKET.getName())).thenReturn(result);
        when(amazonS3.putObject(eq(BUCKET.getName()), any(), any(), any())).thenReturn(null);
        doNothing().when(amazonS3).deleteObject(eq(BUCKET.getName()), any());

        // When
        Status status = uut.call();

        // Then
        assertThat(status, is(SUCCESS));
        verify(amazonS3, times(1)).listObjects(eq(BUCKET.getName()));
        verify(amazonS3, times(0)).putObject(eq(BUCKET.getName()), eq(pathReadmeMd), any(), any());
        verify(amazonS3, times(0)).deleteObject(eq(BUCKET.getName()), any());
        verifyNoMoreInteractions(amazonS3);
    }

    @Test
    public void shouldDeleteOldFile() throws Exception {
        // Given
        String pathReadmeMd = "README.md";
        String contentReadmeMd = "This is a test file";
        this.repository.branch(Constants.MASTER).commit().create();

        ObjectListing result = mock(ObjectListing.class);
        when(result.isTruncated()).thenReturn(false);
        when(result.getObjectSummaries()).thenReturn(new ArrayList<S3ObjectSummary>(2) {{
            S3ObjectSummary summary;

            summary = new S3ObjectSummary();
            summary.setBucketName(BUCKET.getName());
            summary.setKey(".git/test");
            summary.setETag("123");
            add(summary);

            summary = new S3ObjectSummary();
            summary.setBucketName(BUCKET.getName());
            summary.setKey(pathReadmeMd);
            summary.setETag(DigestUtils.md5Hex(contentReadmeMd.getBytes(StringUtils.UTF8)));
            add(summary);
        }});
        when(amazonS3.listObjects(BUCKET.getName())).thenReturn(result);
        when(amazonS3.putObject(eq(BUCKET.getName()), any(), any(), any())).thenReturn(null);
        doNothing().when(amazonS3).deleteObject(eq(BUCKET.getName()), any());

        // When
        Status status = uut.call();

        // Then
        assertThat(status, is(SUCCESS));
        verify(amazonS3, times(1)).listObjects(eq(BUCKET.getName()));
        verify(amazonS3, times(0)).putObject(eq(BUCKET.getName()), eq(pathReadmeMd), any(), any());
        verify(amazonS3, times(1)).deleteObject(eq(BUCKET.getName()), eq(pathReadmeMd));
        verifyNoMoreInteractions(amazonS3);
    }
}
