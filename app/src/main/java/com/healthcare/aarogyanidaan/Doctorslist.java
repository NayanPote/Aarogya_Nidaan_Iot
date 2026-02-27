package com.healthcare.aarogyanidaan;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.widget.SearchView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.healthcare.aarogyanidaan.databinding.ActivityDoctorslistBinding;

public class Doctorslist extends AppCompatActivity {

    private ActivityDoctorslistBinding binding;
    private doctorlistadapter adapter;
    private ArrayList<Users> doctorList;
    private ArrayList<Users> filteredList;
    private FirebaseDatabase database;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDoctorslistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeViews();
        setupRecyclerView();
        fetchDoctorsData();
        setupBackButton();
        setupSearchBar();
        setupChipGroup();
        setupFloatingActionButton();
        setupClearFiltersButton();
    }

    private void initializeViews() {
        auth = FirebaseAuth.getInstance();

        binding.backdoctorslist.setOnClickListener(v -> {
            startActivity(new Intent(Doctorslist.this, patientdashboard.class));
            finish();
        });
    }

    private void setupRecyclerView() {
        doctorList = new ArrayList<>();
        filteredList = new ArrayList<>();
        adapter = new doctorlistadapter(this, filteredList);
        binding.topdoctorlist.setLayoutManager(new LinearLayoutManager(this));
        binding.topdoctorlist.setAdapter(adapter);

        // Set click listener for doctor selection
        adapter.setOnDoctorClickListener((doctor, position) -> {
            Intent intent = new Intent(Doctorslist.this, DoctorDetailsActivity.class);
            intent.putExtra("doctor_id", doctor.getDoctor_id());
            intent.putExtra("doctor_name", doctor.getDoctor_name());
            startActivity(intent);
        });
    }

    private void fetchDoctorsData() {
        showLoading(true);
        database = FirebaseDatabase.getInstance();
        DatabaseReference doctorsRef = database.getReference("doctor");

        doctorsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                doctorList.clear();
                for (DataSnapshot doctorSnapshot : snapshot.getChildren()) {
                    Users doctor = doctorSnapshot.getValue(Users.class);
                    if (doctor != null) {
                        doctorList.add(doctor);
                    }
                }

                // Initially, show all doctors in the filtered list
                filteredList.clear();
                filteredList.addAll(doctorList);

                // Update counts for all doctors
                updateCounts(filteredList);

                updateUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                binding.doccount.setText("0");
                binding.citycount.setText("0");
                binding.specialistcount.setText("0");

                binding.doccount.setVisibility(View.VISIBLE);
                binding.citycount.setVisibility(View.VISIBLE);
                binding.specialistcount.setVisibility(View.VISIBLE);

                binding.emptyView.setVisibility(View.VISIBLE);
                binding.topdoctorlist.setVisibility(View.GONE);
            }
        });
    }

    // Update doctor count + city count + specialization count
    private void updateCounts(ArrayList<Users> doctors) {
        int doctorCount = doctors.size();

        Set<String> citySet = new HashSet<>();
        Set<String> specializationSet = new HashSet<>();

        for (Users doctor : doctors) {
            if (doctor.getDoctor_city() != null && !doctor.getDoctor_city().isEmpty()) {
                citySet.add(doctor.getDoctor_city());
            }
            if (doctor.getDoctor_specialization() != null && !doctor.getDoctor_specialization().isEmpty()) {
                specializationSet.add(doctor.getDoctor_specialization());
            }
        }

        // Doctor count (adding "+" if more than 1)
//        String docCountText = doctorCount + (doctorCount != 1 ? "+" : "");
        String docCountText = String.valueOf(doctorCount);
        binding.doccount.setText(docCountText);
        binding.doccount.setVisibility(View.VISIBLE);

        // City count (number of unique cities)
        binding.citycount.setText(String.valueOf(citySet.size()));
        binding.citycount.setVisibility(View.VISIBLE);

        // Specialization count (unique specialties)
        binding.specialistcount.setText(String.valueOf(specializationSet.size()));
        binding.specialistcount.setVisibility(View.VISIBLE);
    }

    private void setupSearchBar() {
        binding.docsearchBar.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterBySearchText(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterBySearchText(newText);
                return true;
            }
        });
    }

    // Search includes doctor name, specialization, and city
    private void filterBySearchText(String query) {
        ArrayList<Users> searchResults = new ArrayList<>();
        String lowerCaseQuery = query.toLowerCase();

        if (query.isEmpty()) {
            // If no search text, show all doctors or apply just the specialty filter
            applySpecialtyFilter();
        } else {
            // Apply both search text and specialty filter
            String selectedSpecialty = getSelectedSpecialty();
            boolean filterBySpecialty = !selectedSpecialty.equals("All");

            for (Users doctor : doctorList) {
                boolean matchesSearch = (doctor.getDoctor_name() != null && doctor.getDoctor_name().toLowerCase().contains(lowerCaseQuery)) ||
                        (doctor.getDoctor_specialization() != null && doctor.getDoctor_specialization().toLowerCase().contains(lowerCaseQuery)) ||
                        (doctor.getDoctor_city() != null && doctor.getDoctor_city().toLowerCase().contains(lowerCaseQuery));

                boolean matchesSpecialty = !filterBySpecialty ||
                        (doctor.getDoctor_specialization() != null && doctor.getDoctor_specialization().equalsIgnoreCase(selectedSpecialty));

                if (matchesSearch && matchesSpecialty) {
                    searchResults.add(doctor);
                }
            }

            filteredList.clear();
            filteredList.addAll(searchResults);

            // Update counts for filtered doctors
            updateCounts(filteredList);

            updateUI();
        }
    }

    private void applySpecialtyFilter() {
        String selectedSpecialty = getSelectedSpecialty();
        filteredList.clear();

        if (selectedSpecialty.equals("All")) {
            filteredList.addAll(doctorList);
        } else {
            for (Users doctor : doctorList) {
                if (doctor.getDoctor_specialization() != null &&
                        doctor.getDoctor_specialization().equalsIgnoreCase(selectedSpecialty)) {
                    filteredList.add(doctor);
                }
            }
        }

        // Update counts for filtered doctors
        updateCounts(filteredList);
        updateUI();
    }

    private String getSelectedSpecialty() {
        // Get the selected chip text
        for (int i = 0; i < binding.categoryChips.getChildCount(); i++) {
            Chip chip = (Chip) binding.categoryChips.getChildAt(i);
            if (chip.isChecked()) {
                return chip.getText().toString();
            }
        }
        return "All"; // Default if none selected
    }

    private void setupChipGroup() {
        binding.categoryChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            // Apply filters when chip selection changes
            applySpecialtyFilter();
            // Also apply any existing search filter
            String currentQuery = binding.docsearchBar.getQuery().toString();
            if (!currentQuery.isEmpty()) {
                filterBySearchText(currentQuery);
            }
        });
    }

    private void setupFloatingActionButton() {
        binding.fabFilter.setOnClickListener(v -> {
            // Show filter dialog or bottom sheet
            showFilterDialog();
        });
    }

    private void showFilterDialog() {
        // Create and show a filter dialog or bottom sheet
        // This is a placeholder for the filter functionality
        binding.doccount.setText(filteredList.size() + " Doctors");
    }

    private void setupClearFiltersButton() {
        binding.btnClearFilters.setOnClickListener(v -> {
            // Clear all filters
            binding.chipAll.setChecked(true);
            binding.docsearchBar.setQuery("", false);
            filteredList.clear();
            filteredList.addAll(doctorList);

            // Update counts after clearing filters
            updateCounts(filteredList);

            updateUI();
        });
    }

    private void updateUI() {
        showLoading(false);

        if (filteredList.isEmpty()) {
            binding.emptyView.setVisibility(View.VISIBLE);
            binding.topdoctorlist.setVisibility(View.GONE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
            binding.topdoctorlist.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.topdoctorlist.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);
    }

    private void setupBackButton() {
        binding.backdoctorslist.setOnClickListener(v -> {
            onBackPressed(); // Go back to the previous activity
            finish(); // Close the current activity
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
