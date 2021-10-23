package de.sist.gitlab.pipelinemonitor.update;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "PipelineViewerUpdatePersistance", storages = {@Storage("PipelineViewerUpdatePersistance.xml")})
public class UpdateNotificationPersistance implements PersistentStateComponent<UpdateNotificationPersistance> {

    private Integer installedVersion;

    public Integer getInstalledVersion() {
        return installedVersion;
    }

    public void setInstalledVersion(Integer installedVersion) {
        this.installedVersion = installedVersion;
    }

    @Override
    public @Nullable UpdateNotificationPersistance getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull UpdateNotificationPersistance state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static UpdateNotificationPersistance getInstance() {
        return ServiceManager.getService(UpdateNotificationPersistance.class);
    }
}
