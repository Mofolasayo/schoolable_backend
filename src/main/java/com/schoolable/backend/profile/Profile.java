package com.schoolable.backend.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    private UUID id;

    private String email;

    @Column(name = "full_name")
    private String fullName;

    private String role;
    private String avatarUrl;
    private String employeeId;
    private String phone;
    private String department;
    private String status;

    @Column(name = "team_lead_id")
    private UUID teamLeadId;

    private String gender;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "date_joined")
    private OffsetDateTime dateJoined;

    @Column(name = "date_of_birth")
    private java.sql.Date dateOfBirth;

    private String address;
    private String city;
    private String state;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "profile_completed_at")
    private OffsetDateTime profileCompletedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getTeamLeadId() {
        return teamLeadId;
    }

    public void setTeamLeadId(UUID teamLeadId) {
        this.teamLeadId = teamLeadId;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public OffsetDateTime getDateJoined() {
        return dateJoined;
    }

    public void setDateJoined(OffsetDateTime dateJoined) {
        this.dateJoined = dateJoined;
    }

    public java.sql.Date getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(java.sql.Date dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(OffsetDateTime emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public OffsetDateTime getProfileCompletedAt() {
        return profileCompletedAt;
    }

    public void setProfileCompletedAt(OffsetDateTime profileCompletedAt) {
        this.profileCompletedAt = profileCompletedAt;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    @Column(name = "last_seen")
    private OffsetDateTime lastSeen;

    // Performance Management fields
    @Column(name = "employee_level")
    private Integer employeeLevel = 1;

    private String cadre;

    @Column(name = "confirmation_status")
    private String confirmationStatus = "probation";

    @Column(name = "confirmation_date")
    private java.sql.Date confirmationDate;

    @Column(name = "probation_end_date")
    private java.sql.Date probationEndDate;

    @Column(name = "is_team_lead")
    private Boolean isTeamLead = false;

    @Column(name = "team_lead_request_status")
    private String teamLeadRequestStatus = "none";

    @Column(name = "team_lead_requested_at")
    private OffsetDateTime teamLeadRequestedAt;

    @Column(name = "base_salary")
    private java.math.BigDecimal baseSalary;

    @Column(name = "pfp_eligible")
    private Boolean pfpEligible = false;

    public OffsetDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(OffsetDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Integer getEmployeeLevel() {
        return employeeLevel;
    }

    public void setEmployeeLevel(Integer employeeLevel) {
        this.employeeLevel = employeeLevel;
    }

    public String getCadre() {
        return cadre;
    }

    public void setCadre(String cadre) {
        this.cadre = cadre;
    }

    public String getConfirmationStatus() {
        return confirmationStatus;
    }

    public void setConfirmationStatus(String confirmationStatus) {
        this.confirmationStatus = confirmationStatus;
    }

    public java.sql.Date getConfirmationDate() {
        return confirmationDate;
    }

    public void setConfirmationDate(java.sql.Date confirmationDate) {
        this.confirmationDate = confirmationDate;
    }

    public java.sql.Date getProbationEndDate() {
        return probationEndDate;
    }

    public void setProbationEndDate(java.sql.Date probationEndDate) {
        this.probationEndDate = probationEndDate;
    }

    public Boolean getIsTeamLead() {
        return isTeamLead;
    }

    public void setIsTeamLead(Boolean isTeamLead) {
        this.isTeamLead = isTeamLead;
    }

    public String getTeamLeadRequestStatus() {
        return teamLeadRequestStatus;
    }

    public void setTeamLeadRequestStatus(String teamLeadRequestStatus) {
        this.teamLeadRequestStatus = teamLeadRequestStatus;
    }

    public OffsetDateTime getTeamLeadRequestedAt() {
        return teamLeadRequestedAt;
    }

    public void setTeamLeadRequestedAt(OffsetDateTime teamLeadRequestedAt) {
        this.teamLeadRequestedAt = teamLeadRequestedAt;
    }

    public java.math.BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(java.math.BigDecimal baseSalary) {
        this.baseSalary = baseSalary;
    }

    public Boolean getPfpEligible() {
        return pfpEligible;
    }

    public void setPfpEligible(Boolean pfpEligible) {
        this.pfpEligible = pfpEligible;
    }

    // HR Management fields for organizational structure
    @Column(name = "job_level")
    private Integer jobLevel = 1;

    @Column(name = "grade")
    private Integer grade = 2;

    @Column(name = "hire_date")
    private java.sql.Date hireDate;

    @Column(name = "probation_status")
    private String probationStatus = "not_applicable";

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience = 0;

    public Integer getJobLevel() {
        return jobLevel;
    }

    public void setJobLevel(Integer jobLevel) {
        this.jobLevel = jobLevel;
    }

    public Integer getGrade() {
        return grade;
    }

    public void setGrade(Integer grade) {
        this.grade = grade;
    }

    public java.sql.Date getHireDate() {
        return hireDate;
    }

    public void setHireDate(java.sql.Date hireDate) {
        this.hireDate = hireDate;
    }

    public void setHireDate(java.time.LocalDate hireDate) {
        this.hireDate = hireDate != null ? java.sql.Date.valueOf(hireDate) : null;
    }

    public String getProbationStatus() {
        return probationStatus;
    }

    public void setProbationStatus(String probationStatus) {
        this.probationStatus = probationStatus;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    @Column(name = "reference_face_url")
    private String referenceFaceUrl;

    @Column(name = "reference_face_registered_at")
    private OffsetDateTime referenceFaceRegisteredAt;

    @Column(name = "checkin_device_id")
    private String checkinDeviceId;

    @Column(name = "checkin_device_info")
    private String checkinDeviceInfo;

    @Column(name = "checkin_device_registered_at")
    private OffsetDateTime checkinDeviceRegisteredAt;

    public String getReferenceFaceUrl() {
        return referenceFaceUrl;
    }

    public void setReferenceFaceUrl(String referenceFaceUrl) {
        this.referenceFaceUrl = referenceFaceUrl;
    }

    public OffsetDateTime getReferenceFaceRegisteredAt() {
        return referenceFaceRegisteredAt;
    }

    public void setReferenceFaceRegisteredAt(OffsetDateTime referenceFaceRegisteredAt) {
        this.referenceFaceRegisteredAt = referenceFaceRegisteredAt;
    }

    public String getCheckinDeviceId() {
        return checkinDeviceId;
    }

    public void setCheckinDeviceId(String checkinDeviceId) {
        this.checkinDeviceId = checkinDeviceId;
    }

    public String getCheckinDeviceInfo() {
        return checkinDeviceInfo;
    }

    public void setCheckinDeviceInfo(String checkinDeviceInfo) {
        this.checkinDeviceInfo = checkinDeviceInfo;
    }

    public OffsetDateTime getCheckinDeviceRegisteredAt() {
        return checkinDeviceRegisteredAt;
    }

    public void setCheckinDeviceRegisteredAt(OffsetDateTime checkinDeviceRegisteredAt) {
        this.checkinDeviceRegisteredAt = checkinDeviceRegisteredAt;
    }
}
