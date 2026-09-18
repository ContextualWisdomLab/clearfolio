cat << 'INNER_EOF' > /tmp/AdminControllerTest.patch
<<<<<<< SEARCH
    @Test
    void deleteJobReturnsNotFoundWhenNotExists() {
=======
    @Test
    void getAllJobsFiltersOutJobsNotBelongingToTenant() {
        ConversionJob job1 = new ConversionJob(UUID.randomUUID(), "tenant-1", "subject-1", "a.pdf", "application/pdf", "hash-a", 100L, 1);
        ConversionJob job2 = new ConversionJob(UUID.randomUUID(), "tenant-2", "subject-2", "b.pdf", "application/pdf", "hash-b", 100L, 1);
        when(conversionService.getAllJobs()).thenReturn(Arrays.asList(job1, job2));

        webTestClient.get()
                .uri("/api/v1/admin/convert/jobs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobs.length()").isEqualTo(1)
                .jsonPath("$.jobs[0].fileName").isEqualTo("a.pdf");
    }

    @Test
    void deleteJobReturnsNotFoundWhenNotExists() {
>>>>>>> REPLACE
INNER_EOF
python3 -c "import sys; content = open('src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java').read(); patch = open('/tmp/AdminControllerTest.patch').read(); search = patch.split('<<<<<<< SEARCH')[1].split('=======')[0].strip('\n'); replace = patch.split('=======')[1].split('>>>>>>> REPLACE')[0].strip('\n'); new_content = content.replace(search, replace); open('src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java', 'w').write(new_content);"
mvn -B verify -Dtest=AdminControllerTest
