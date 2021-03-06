package mirror;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.stub.StreamObserver;
import mirror.MirrorGrpc.MirrorStub;

/**
 * Provides basic connection detection by explicit pings.
 *
 * In theory grpc has some Http2 ping facilities, but I wasn't sure
 * how to use it.
 */
public interface ConnectionDetector {

  void blockUntilConnected(MirrorStub stub);

  boolean isAvailable(MirrorStub stub);

  /** A stub/noop detector for unit tests. */
  public static class Noop implements ConnectionDetector {
    @Override
    public void blockUntilConnected(MirrorStub stub) {
    }

    @Override
    public boolean isAvailable(MirrorStub stub) {
      return true;
    }
  }

  /** A detector that uses our app-specific PingRequest/PingResponse. */
  public static class Impl implements ConnectionDetector {
    private static final Duration durationBetweenDetections = Duration.ofSeconds(10);

    @Override
    public boolean isAvailable(MirrorStub stub) {
      AtomicBoolean available = new AtomicBoolean(false);
      CountDownLatch done = new CountDownLatch(1);
      stub.ping(PingRequest.newBuilder().build(), new StreamObserver<PingResponse>() {
        @Override
        public void onNext(PingResponse value) {
          available.set(true);
        }

        @Override
        public void onError(Throwable t) {
          done.countDown();
        }

        @Override
        public void onCompleted() {
          done.countDown();
        }
      });
      Utils.resetIfInterrupted(() -> done.await(1_000, TimeUnit.MILLISECONDS));
      return available.get();
    }

    @Override
    public void blockUntilConnected(MirrorStub stub) {
      while (!isAvailable(stub)) {
        Utils.resetIfInterrupted(() -> Thread.sleep(durationBetweenDetections.toMillis()));
      }
    }
  }

}
