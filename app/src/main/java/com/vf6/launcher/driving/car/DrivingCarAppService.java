package com.vf6.launcher.driving.car;

import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

public final class DrivingCarAppService extends CarAppService {
    @Override
    public HostValidator createHostValidator() {
        // Development/sideload build. Production builds should validate known hosts.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @Override
    public Session onCreateSession() {
        return new DrivingSession();
    }
}
