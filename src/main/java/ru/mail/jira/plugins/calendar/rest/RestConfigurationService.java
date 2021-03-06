package ru.mail.jira.plugins.calendar.rest;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.filter.SearchRequestService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.ProjectService;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.sharing.SharedEntityColumn;
import com.atlassian.jira.sharing.search.SharedEntitySearchContext;
import com.atlassian.jira.sharing.search.SharedEntitySearchParameters;
import com.atlassian.jira.sharing.search.SharedEntitySearchParametersBuilder;
import com.atlassian.jira.sharing.search.SharedEntitySearchResult;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.I18nHelper;
import org.apache.commons.lang3.StringUtils;
import ru.mail.jira.plugins.calendar.rest.dto.DateField;
import ru.mail.jira.plugins.calendar.rest.dto.IssueSourceDto;
import ru.mail.jira.plugins.calendar.rest.dto.PermissionItemDto;
import ru.mail.jira.plugins.calendar.rest.dto.PermissionSubjectDto;
import ru.mail.jira.plugins.calendar.rest.dto.SelectItemDto;
import ru.mail.jira.plugins.calendar.service.CalendarEventService;
import ru.mail.jira.plugins.calendar.service.CalendarServiceImpl;
import ru.mail.jira.plugins.calendar.service.PermissionUtils;
import ru.mail.jira.plugins.commons.RestExecutor;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/calendar/config")
@Produces(MediaType.APPLICATION_JSON)
public class RestConfigurationService {
    private final ApplicationProperties applicationProperties;
    private final AvatarService avatarService;
    private final CustomFieldManager customFieldManager;
    private final GlobalPermissionManager globalPermissionManager;
    private final GroupManager groupManager;
    private final I18nHelper i18nHelper;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final ProjectManager projectManager;
    private final ProjectService projectService;
    private final ProjectRoleManager projectRoleManager;
    private final SearchRequestService searchRequestService;
    private final SearchService searchService;
    private final UserManager userManager;

    public RestConfigurationService(ApplicationProperties applicationProperties, AvatarService avatarService, CustomFieldManager customFieldManager,
                                    GlobalPermissionManager globalPermissionManager, GroupManager groupManager, I18nHelper i18nHelper,
                                    JiraAuthenticationContext jiraAuthenticationContext,
                                    ProjectManager projectManager, ProjectService projectService,
                                    ProjectRoleManager projectRoleManager, SearchRequestService searchRequestService, SearchService searchService, UserManager userManager) {
        this.applicationProperties = applicationProperties;
        this.avatarService = avatarService;
        this.customFieldManager = customFieldManager;
        this.globalPermissionManager = globalPermissionManager;
        this.groupManager = groupManager;
        this.i18nHelper = i18nHelper;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.projectManager = projectManager;
        this.projectService = projectService;
        this.projectRoleManager = projectRoleManager;
        this.searchRequestService = searchRequestService;
        this.searchService = searchService;
        this.userManager = userManager;
    }

    @GET
    @Path("/props")
    public Response getTimeFormat() {
        return new RestExecutor<Map<String, String>>() {
            @Override
            protected Map<String, String> doAction() throws Exception {
                Map<String, String> result = new HashMap<String, String>();
                result.put("timeFormat", applicationProperties.getDefaultBackedString(APKeys.JIRA_LF_DATE_TIME));
                return result;
            }
        }.getResponse();
    }

    @GET
    @Path("/displayedFields")
    public Map<String, String> getDisplayedFields() {
        Map<String, String> result = new LinkedHashMap<String, String>(CalendarServiceImpl.DISPLAYED_FIELDS.size());
        for (String field : CalendarServiceImpl.DISPLAYED_FIELDS)
            result.put(field, i18nHelper.getText(field));
        for (CustomField customField : customFieldManager.getCustomFieldObjects())
            result.put(customField.getId(), customField.getName());
        return result;
    }

    @GET
    @Path("/dateFields")
    public List<DateField> getDateFields() {
        List<DateField> dateFields = new ArrayList<DateField>();
        dateFields.add(DateField.of(CalendarEventService.CREATED_DATE_KEY, i18nHelper.getText("issue.field.created")));
        dateFields.add(DateField.of(CalendarEventService.UPDATED_DATE_KEY, i18nHelper.getText("issue.field.updated")));
        dateFields.add(DateField.of(CalendarEventService.RESOLVED_DATE_KEY, i18nHelper.getText("common.concepts.resolved")));
        dateFields.add(DateField.of(CalendarEventService.DUE_DATE_KEY, i18nHelper.getText("issue.field.duedate")));

        for (CustomField customField : customFieldManager.getCustomFieldObjects())
            if (customField.getCustomFieldType() instanceof com.atlassian.jira.issue.fields.DateField)
                dateFields.add(DateField.of(customField.getId(), customField.getName()));

        return dateFields;
    }

    @GET
    @Path("/eventSources/filter")
    public Response getFilterEventSources(@QueryParam("filter") final String filter) {
        return new RestExecutor<IssueSourceDto>() {
            @Override
            protected IssueSourceDto doAction() throws Exception {
                IssueSourceDto issueSourceDto = new IssueSourceDto();
                fillFilterSources(jiraAuthenticationContext.getUser(), issueSourceDto, filter);
                return issueSourceDto;
            }
        }.getResponse();
    }

    @GET
    @Path("/eventSources/project")
    public Response getProjectEventSources(@QueryParam("filter") final String filter) {
        return new RestExecutor<IssueSourceDto>() {
            @Override
            protected IssueSourceDto doAction() throws Exception {
                IssueSourceDto issueSourceDto = new IssueSourceDto();
                fillProjectSources(issueSourceDto, filter);
                return issueSourceDto;
            }
        }.getResponse();
    }

    @GET
    @Path("permission/subjects")
    public Response getPermissionSubjects(@QueryParam("filter") final String filter) {
        return new RestExecutor<PermissionSubjectDto>() {
            @Override
            protected PermissionSubjectDto doAction() throws Exception {
                PermissionSubjectDto subjectDto = new PermissionSubjectDto();
                fillUsers(filter, subjectDto);
                fillGroups(jiraAuthenticationContext.getUser(), filter, subjectDto);
                fillProjectRoles(jiraAuthenticationContext.getUser(), filter, subjectDto);
                return subjectDto;
            }
        }.getResponse();
    }

    @GET
    @Path("jql/count")
    public Response countIssues(@QueryParam("jql") final String jql) {
        return new RestExecutor<Map<String, Object>>() {
            @Override
            protected Map<String, Object> doAction() throws Exception {
                ApplicationUser user = jiraAuthenticationContext.getUser();
                Map<String, Object> result = new HashMap<String, Object>();
                if (StringUtils.isNotBlank(jql)) {
                    SearchService.ParseResult parseResult = searchService.parseQuery(user, jql);
                    if (parseResult.isValid())
                        result.put("issueCount", searchService.searchCount(user, parseResult.getQuery()));
                }
                return result;
            }
        }.getResponse();
    }

    private void fillProjectRoles(ApplicationUser user, String filter, PermissionSubjectDto subjectDto) {
        List<PermissionItemDto> result = new ArrayList<PermissionItemDto>();
        filter = filter.trim().toLowerCase();

        List<Project> allProjects = isAdministrator(user) ? projectManager.getProjectObjects() : projectService.getAllProjects(user).get();
        Collection<ProjectRole> projectRoles = projectRoleManager.getProjectRoles();
        if (StringUtils.isEmpty(filter)) {
            for (Project project : allProjects) {
                for (ProjectRole role : projectRoles)
                    result.add(PermissionItemDto.buildProjectRoleDto(PermissionUtils.projectRoleSubject(project.getId(), role.getId()),
                                                                     String.format("%s (%s)", project.getName(), project.getKey()), role.getName(),
                                                                     null,
                                                                     String.format("projectavatar?pid=%d&avatarId=%d&size=xxmall", project.getId(), project.getAvatar().getId())));
                if (result.size() >= 10)
                    break;
            }
            subjectDto.setProjectRolesCount(projectRoles.size() * allProjects.size());
        } else {
            int count = 0;
            long lastAddedProjectId = -1;
            for (Project project : allProjects)
                for (ProjectRole role : projectRoles)
                    if (StringUtils.containsIgnoreCase(role.getName(), filter)
                            || StringUtils.containsIgnoreCase(project.getName(), filter)
                            || StringUtils.containsIgnoreCase(project.getKey(), filter)) {
                        count++;
                        if (result.size() < 10 || lastAddedProjectId == project.getId()) {
                            lastAddedProjectId = project.getId();
                            result.add(PermissionItemDto.buildProjectRoleDto(PermissionUtils.projectRoleSubject(project.getId(), role.getId()),
                                                                             String.format("%s (%s)", project.getName(), project.getKey()), role.getName(),
                                                                             null,
                                                                             String.format("projectavatar?pid=%d&avatarId=%d&size=xxmall", project.getId(), project.getAvatar().getId())));
                        }
                    }
            subjectDto.setProjectRolesCount(count);
        }
        subjectDto.setProjectRoles(result);

    }

    private void fillGroups(ApplicationUser user, String filter, PermissionSubjectDto subjectDto) {
        filter = filter.trim().toLowerCase();

        Collection<Group> groups = isAdministrator(user) ? groupManager.getAllGroups() : groupManager.getGroupsForUser(user.getName());
        List<PermissionItemDto> result = new ArrayList<PermissionItemDto>();

        if (StringUtils.isEmpty(filter)) {
            for (Group group : groups) {
                result.add(PermissionItemDto.buildGroupDto(group.getName(), group.getName(), null));
                if (result.size() >= 10)
                    break;
            }
            subjectDto.setGroupsCount(groups.size());
        } else {
            int count = 0;
            for (Group group : groups)
                if (StringUtils.containsIgnoreCase(group.getName(), filter)) {
                    count++;
                    if (result.size() < 10)
                        result.add(PermissionItemDto.buildGroupDto(group.getName(), group.getName(), null));
                }
            subjectDto.setGroupsCount(count);
        }
        subjectDto.setGroups(result);
    }

    private void fillUsers(String filter, PermissionSubjectDto subjectDto) {
        List<PermissionItemDto> result = new ArrayList<PermissionItemDto>();
        if (!globalPermissionManager.hasPermission(GlobalPermissionKey.USER_PICKER, jiraAuthenticationContext.getLoggedInUser()))
            return;
        filter = filter.trim().toLowerCase();

        Collection<ApplicationUser> users = userManager.getAllApplicationUsers();

        if (StringUtils.isEmpty(filter)) {
            for (ApplicationUser user : users) {
                result.add(PermissionItemDto.buildUserDto(user.getKey(),
                                                          user.getDisplayName(), user.getEmailAddress(), user.getName(),
                                                          null,
                                                          getUserAvatarSrc(user)));
                if (result.size() >= 10)
                    break;
            }
            subjectDto.setUsersCount(users.size());
        } else {
            int count = 0;
            for (ApplicationUser user : users) {
                if (user.isActive() && (StringUtils.containsIgnoreCase(user.getDisplayName(), filter)
                        || StringUtils.containsIgnoreCase(user.getName(), filter)
                        || StringUtils.containsIgnoreCase(user.getEmailAddress(), filter))) {
                    count++;
                    if (result.size() < 10)
                        result.add(PermissionItemDto.buildUserDto(user.getKey(),
                                                                  user.getDisplayName(), user.getEmailAddress(), user.getName(),
                                                                  null,
                                                                  getUserAvatarSrc(user)));
                }
            }
            subjectDto.setUsersCount(count);
        }
        subjectDto.setUsers(result);
    }

    private void fillProjectSources(IssueSourceDto issueSourceDto, String filter) {
        List<SelectItemDto> result = new ArrayList<SelectItemDto>();
        filter = filter.trim().toLowerCase();
        int total = 0;
        List<Project> allProjects = projectService.getAllProjects(jiraAuthenticationContext.getUser()).get();
        for (Project project : allProjects) {
            if (project.getName().toLowerCase().contains(filter) || project.getKey().toLowerCase().contains(filter)) {
                if (result.size() < 10)
                    result.add(new SelectItemDto(String.valueOf(project.getId()), String.format("%s (%s)", project.getName(), project.getKey()), project.getAvatar().getId()));
                total++;
            }
        }

        Collections.sort(result, new Comparator<SelectItemDto>() {
            @Override
            public int compare(SelectItemDto fProject, SelectItemDto sProject) {
                return fProject.getText().toLowerCase().compareTo(sProject.getText().toLowerCase());
            }
        });

        issueSourceDto.setTotalProjectsCount(total);
        issueSourceDto.setProjects(result);
    }

    private void fillFilterSources(ApplicationUser user, IssueSourceDto issueSourceDto, String filter) {
        filter = StringUtils.trimToNull(filter);
        SharedEntitySearchParametersBuilder builder = new SharedEntitySearchParametersBuilder();
        builder.setName(filter);
        builder.setTextSearchMode(SharedEntitySearchParameters.TextSearchMode.WILDCARD);
        builder.setSortColumn(SharedEntityColumn.NAME, true);
        builder.setEntitySearchContext(SharedEntitySearchContext.USE);
        if (filter == null) {
            Collection<SearchRequest> favorites = searchRequestService.getFavouriteFilters(user);
            Collection<SearchRequest> owned = searchRequestService.getOwnedFilters(user);
            issueSourceDto.setMyFilters(SelectItemDto.buildSelectSearchRequestItemDtos(owned));
            issueSourceDto.setFavouriteFilters(SelectItemDto.buildSelectSearchRequestItemDtos(favorites));
            if (owned.isEmpty() && favorites.isEmpty()) {
                SharedEntitySearchResult<SearchRequest> searchResults = searchRequestService.search(new JiraServiceContextImpl(user), builder.toSearchParameters(), 0, 10);
                issueSourceDto.setFilters(SelectItemDto.buildSelectSearchRequestItemDtos(searchResults.getResults()));
            }
        } else {
            SharedEntitySearchResult<SearchRequest> searchResults = searchRequestService.search(new JiraServiceContextImpl(user), builder.toSearchParameters(), 0, Integer.MAX_VALUE);
            issueSourceDto.setFilters(SelectItemDto.buildSelectSearchRequestItemDtos(searchResults.getResults()));
        }
    }

    private String getUserAvatarSrc(ApplicationUser user) {
        return avatarService.getAvatarURL(user, userManager.getUserByKey(user.getKey()), Avatar.Size.SMALL).toString();
    }

    private boolean isAdministrator(ApplicationUser user) {
        return globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user);
    }
}
