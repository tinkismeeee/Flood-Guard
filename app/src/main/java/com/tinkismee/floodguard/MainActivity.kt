package com.tinkismee.floodguard

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNavBar : BottomNavigationView
    private lateinit var fragmentContainer : FragmentContainerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainerView, map()).commit()

        initVars()
        bottomNavBar.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.mapBtn -> replaceFragment(map())
                R.id.sensorBtn -> replaceFragment(sensor_data())
                R.id.sosBtn -> replaceFragment(ManageSOSFragment())
//                R.id.resourceBtn -> replaceFragment(about_fragment())
//                R.id.reportBtn -> replaceFragment(about_fragment())
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    private fun initVars() {
        bottomNavBar = findViewById<BottomNavigationView>(R.id.bottomNavBar)
        fragmentContainer = findViewById<FragmentContainerView>(R.id.fragmentContainerView)
    }

    private fun replaceFragment(fragment: Fragment) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (currentFragment?.javaClass == fragment.javaClass) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerView, fragment)
            .commit()
    }

}